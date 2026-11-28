package com.xuan.policy;

import com.xuan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 上传文件二进制内容校验规则，按图片、音频和歌词的真实文件头及结构校验，不信任声明的 Content-Type。
 */
@Component
@RequiredArgsConstructor
public class FileContentPolicy {

    private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[[0-9]{1,6}:[0-5][0-9](?:\\.[0-9]{1,3})?]");

    private final JsonMapper json;

    /**
     * 只信 ImageIO 识别结果，先读尺寸限制内存，再实际解码；声明的 Content-Type 不参与。
     */
    public BufferedImage validateImage(Path path, String extension) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            // 1. 通过 ImageIO 识别真实图片格式，校验与扩展名一致。
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean matched = switch (extension) {
                    case "png" -> "png".equals(format);
                    case "jpg", "jpeg" -> "jpeg".equals(format);
                    case "webp" -> "webp".equals(format);
                    default -> false;
                };
                if (!matched) {
                    throw invalid();
                }

                // 2. 解码前先检查宽高与总像素上限，防止图片炸弹耗尽内存。
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 25000000) {
                    throw invalid();
                }

                // 3. 注册警告监听并完整解码，确保图片数据无损坏或畸形块。
                reader.addIIOReadWarningListener((source, warning) -> {
                    throw invalid();
                });
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalid();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    /**
     * 按文件头校验音频容器和首个音频帧，直接读取临时文件必要字节，不将整个音频文件载入堆内存。
     */
    public void validateAudio(Path path, long size, String extension) throws IOException {
        boolean valid = false;
        if (size >= 42 && "flac".equals(extension)) {
            byte[] header = readPrefix(path, 42);
            valid = header.length >= 42 && ascii(header, 0, 4).equals("fLaC") && (header[4] & 0x7f) == 0
                    && header[5] == 0 && header[6] == 0 && header[7] == 34;
        } else if (size >= 44 && "wav".equals(extension)) {
            // 交给 JDK 直接从文件识别音频格式，保留带 LIST/JUNK 等附加块的合法 WAV。
            try {
                var format = AudioSystem.getAudioFileFormat(path.toFile());
                valid = AudioFileFormat.Type.WAVE.equals(format.getType());
            } catch (IOException | UnsupportedAudioFileException exception) {
                valid = false;
            }
        } else if (size >= 35 && "ogg".equals(extension)) {
            byte[] header = readPrefix(path, 300);
            if (header.length >= 35) {
                int segments = header[26] & 0xff;
                int payload = 27 + segments;
                valid = ascii(header, 0, 4).equals("OggS") && header[4] == 0 && segments > 0
                        && payload + 8 <= header.length
                        && (ascii(header, payload, 8).equals("OpusHead")
                        || (header[payload] == 1 && ascii(header, payload + 1, 6).equals("vorbis")));
            }
        } else if (size >= 4 && "mp3".equals(extension)) {
            byte[] id3 = readPrefix(path, 10);
            long offset = 0;
            if (id3.length >= 10 && ascii(id3, 0, 3).equals("ID3")) {
                if ((id3[6] | id3[7] | id3[8] | id3[9]) < 0) {
                    throw invalid();
                }
                offset = 10L + (id3[6] << 21 | id3[7] << 14 | id3[8] << 7 | id3[9]);
                if (id3[3] == 4 && (id3[5] & 0x10) != 0) {
                    offset += 10;
                }
            }
            if (offset + 4 <= size) {
                byte[] frame = readAt(path, offset, 4);
                if (frame.length == 4) {
                    int version = (frame[1] >> 3) & 3;
                    int bitrateIndex = (frame[2] >> 4) & 15;
                    int rateIndex = (frame[2] >> 2) & 3;
                    valid = (frame[0] & 0xff) == 0xff && (frame[1] & 0xe6) == 0xe2
                            && version != 1 && bitrateIndex > 0 && bitrateIndex < 15 && rateIndex < 3;
                    if (valid) {
                        int[] rates = {44100, 48000, 32000};
                        int[] bitrates = version == 3
                                ? new int[]{0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320}
                                : new int[]{0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
                        int rate = rates[rateIndex];
                        if (version != 3) {
                            rate /= version == 2 ? 2 : 4;
                        }
                        int frameBytes = (version == 3 ? 144000 : 72000) * bitrates[bitrateIndex] / rate
                                + ((frame[2] >> 1) & 1);
                        valid = offset + frameBytes <= size;
                    }
                }
            }
        }
        if (!valid) {
            throw invalid();
        }
    }

    /**
     * 歌词必须为 UTF-8 文本；JSON 行使用秒数 time 与纯文本 text。
     */
    public void validateLyrics(byte[] bytes, String extension) {
        // 1. 严格按 UTF-8 解码并去除可选 BOM，拒绝空白和非法控制字符。
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw invalid();
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.isBlank() || text.codePoints().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\r' && character != '\t')) {
            throw invalid();
        }

        // 2. LRC 须至少包含一条时间戳；JSON 须为非空数组且每行含非负有限秒数 time 与文本 text。
        if ("lrc".equals(extension) && !LRC_TIMESTAMP.matcher(text).find()) {
            throw invalid();
        }
        if (!"json".equals(extension)) {
            return;
        }
        try {
            var lines = json.readTree(text);
            if (!lines.isArray() || lines.isEmpty()) {
                throw invalid();
            }
            for (var line : lines) {
                if (!line.isObject() || !line.path("time").isNumber() || !line.path("text").isString()
                        || !Double.isFinite(line.path("time").asDouble()) || line.path("time").asDouble() < 0) {
                    throw invalid();
                }
            }
        } catch (JacksonException exception) {
            throw invalid();
        }
    }

    /**
     * 内容校验失败不回显文件内容、名称或解析器异常。
     */
    public BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "文件内容无法识别，请确认文件未损坏且与扩展名一致，重新选择后上传。");
    }

    /**
     * 读取文件开头最多 maxBytes 字节，供二进制头校验使用。
     */
    private byte[] readPrefix(Path path, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(maxBytes);
        }
    }

    /**
     * 从文件指定偏移读取固定字节，供 MP3 跳过 ID3 标签后校验帧头。
     */
    private byte[] readAt(Path path, long offset, int length) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(length);
            int total = 0;
            while (total < length) {
                int read = channel.read(buffer, offset + total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            if (total == length) {
                return buffer.array();
            }
            byte[] partial = new byte[total];
            System.arraycopy(buffer.array(), 0, partial, 0, total);
            return partial;
        }
    }

    /**
     * 仅用于已检查长度的音频头，不解码用户文本。
     */
    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
