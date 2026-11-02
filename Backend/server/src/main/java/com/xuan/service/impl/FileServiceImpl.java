package com.xuan.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.FileQueryDTO;
import com.xuan.entity.BlogArticleContent;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.FileMapper;
import com.xuan.mapper.MusicMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.FileContent;
import com.xuan.policy.ArticleAttachmentPolicy;
import com.xuan.policy.MusicAttachmentPolicy;
import com.xuan.properties.ImageProperties;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IFileService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.FileStore;
import com.xuan.utils.FileUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.ImageCompressUtils;
import com.xuan.vo.BatchFileResultVO;
import com.xuan.vo.BatchFileResultsVO;
import com.xuan.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 文件存储独立处理，文章和音乐分别检查引用；所有写入先锁操作者，文件锁始终早于文章锁。
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, BlogFile> implements IFileService {

    private final ObjectProvider<IFileService> fileService;

    private final FileMapper files;
    private final ArticleMapper articles;
    private final MusicMapper music;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final ArticleAttachmentPolicy articleResources;
    private final MusicAttachmentPolicy musicResources;
    private final FileStore storage;
    private final ImageProperties imageProperties;
    private final JsonMapper json;

    /** 分页查询文件，在同一个只读事务中查询文件资料和总数，筛选上传者不能扩大查询权限的 SELF 范围。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<FileVO> listFiles(FileQueryDTO query) {
        Long filter = query.uploaderId() == null ? null : IdUtils.parse(query.uploaderId());
        return PageResult.from(files.filePage(new Page<>(query.page(), query.pageSize()), query,
                requireAdmin("query"), filter).convert(this::view));
    }

    /** 根据id查询文件资料，资料不可编辑，不返回版本或内部存储信息。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FileVO detail(long id) {
        return view(requireFile(articles.scopedFile(id, requireAdmin("query"))));
    }

    /** 上传文件，先临时保存并校验，再写入存储和数据库；日志写入或事务提交失败时清理新文件。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "upload", target = "blog:file", permission = "blog:file:upload")
    public FileVO upload(MultipartFile upload) {
        users.lockById(sessions.currentUserId());
        requireAdmin("upload");
        String name = upload.getOriginalFilename();
        name = name == null ? "" : name.replaceAll("[/\\\\\\p{Cc}]", "").strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 255 || upload.isEmpty()) {
            throw invalid();
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        int limit = FileUtils.maxBytes(extension);
        if (name.lastIndexOf('.') < 0 || limit == 0) {
            throw invalid();
        }
        if (upload.getSize() > limit) {
            throw tooLarge();
        }
        Path temporary = storage.temporary();
        BlogFile row = new BlogFile();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务结束后清理临时文件，未提交成功时同时清理新上传文件。
             */
            @Override
            public void afterCompletion(int status) {
                storage.cleanup(temporary, status == STATUS_COMMITTED ? null : row);
            }
        });
        try (InputStream input = upload.getInputStream()) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw tooLarge();
            }
            if (bytes.length == 0) {
                throw invalid();
            }
            Files.write(temporary, bytes);
            String mime = FileUtils.mimeType(extension);
            if (mime.startsWith("image/")) {
                BufferedImage image = validateImage(temporary, extension);
                byte[] compressed = ImageCompressUtils.compress(bytes, image, extension, imageProperties);
                if (compressed != bytes) {
                    Files.write(temporary, compressed);
                    bytes = compressed;
                }
            } else if (mime.startsWith("audio/")) {
                validateAudio(bytes, extension);
            } else {
                validateLyrics(bytes, extension);
            }
            row.setUploaderId(sessions.currentUserId());
            row.setOriginalName(name);
            row.setMimeType(mime);
            row.setSizeBytes(BigInteger.valueOf(bytes.length));
            storage.store(temporary, extension, row);
            // 内容处理和存储可能耗时，提交前还要确认原 Token 没被撤销。
            requireAdmin("upload");
            files.insert(row);
            return view(files.selectById(row.getFileId()));
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 原文件读取不依赖任何文章，等待文件锁后重新检查原 Token、权限和上传者范围。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FileContent original(long id) {
        requireAdmin("query");
        articles.lockFile(id);
        return content(requireFile(articles.scopedFile(id, requireAdmin("query"))));
    }

    /** 删除文件，锁住文件后检查所有文章的正文和封面引用；先删除存储文件，再一起提交文件记录删除和成功日志。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:file", permission = "blog:file:delete", targetIdArgument = 0)
    public void delete(long id) {
        users.lockById(sessions.currentUserId());
        requireAdmin("delete");
        articles.lockFile(id);
        BlogFile row = requireFile(articles.scopedFile(id, requireAdmin("delete")));
        if (music.hasFileReference(musicResources.fileUrl(id))) {
            throw new BusinessException(409, "RESOURCE_IN_USE", "文件仍被音乐引用");
        }
        long after = 0;
        while (true) {
            List<FileMapper.ArticleReference> batch = files.referencesAfter(after);
            if (batch.isEmpty()) {
                break;
            }
            for (FileMapper.ArticleReference article : batch) {
                if (article.getMarkdownContent() == null) {
                    throw unavailable();
                }
                if (articleResources.inspect(article.getArticleId(), article.getMarkdownContent(), article.getCoverUrl())
                        .fileIds().contains(id)) {
                    throw new BusinessException(409, "RESOURCE_IN_USE", "文件仍被文章引用");
                }
                after = article.getArticleId();
            }
        }
        Long scope = requireAdmin("delete");
        requireFile(articles.scopedFile(id, scope));
        storage.delete(row);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 文件已删除但数据库未提交时，记录清理失败，供后续重试。
             */
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR,
                            "文件对象已删除但元数据未提交，可重试删除完成清理");
                }
            }
        });
        files.deleteScoped(id, scope);
    }

    /** 读取文章附件，先锁文件再锁文章，重新检查公开状态或文章、文件各自的权限范围，并确认正文或封面引用了该文件。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FileContent attachment(long articleId, long fileId, boolean admin) {
        Long author = null;
        if (admin) {
            requireAdmin("query");
            author = articleScope();
        }
        articles.lockFile(fileId);
        articles.scoped(articleId, author, true);
        BlogFile file = requireFile(articles.scopedFile(fileId, admin ? requireAdmin("query") : null));
        ArticleRow article = admin ? articles.scoped(articleId, articleScope(), false)
                : articles.publicById(articleId, LocalDateTime.now(ZoneOffset.UTC));
        if (article == null) {
            throw missing();
        }
        BlogArticleContent body = articles.content(articleId);
        if (body == null) {
            throw unavailable();
        }
        if (!articleResources.inspect(articleId, body.getMarkdownContent(), article.getCoverUrl()).fileIds()
                .contains(fileId)) {
            throw missing();
        }
        return content(file);
    }

    /** 文件锁早于音乐锁；隐藏曲目不能通过文件地址匿名读取。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FileContent musicAttachment(long fileId) {
        articles.lockFile(fileId);
        BlogFile file = requireFile(articles.scopedFile(fileId, null));
        if (music.lockPublicFileReference(musicResources.fileUrl(fileId)) == null) {
            throw missing();
        }
        return content(file);
    }

    /** 校验音频容器和首个音频头，不信扩展名或浏览器声明的 Content-Type。 */
    private void validateAudio(byte[] bytes, String extension) {
        boolean valid = false;
        if (bytes.length >= 42 && "flac".equals(extension)) {
            valid = ascii(bytes, 0, 4).equals("fLaC") && (bytes[4] & 0x7f) == 0
                    && bytes[5] == 0 && bytes[6] == 0 && bytes[7] == 34;
        } else if (bytes.length >= 44 && "wav".equals(extension)) {
            // 交给 JDK 识别音频格式，保留带 LIST/JUNK 等附加块的合法 WAV。
            try (var input = new ByteArrayInputStream(bytes)) {
                var format = AudioSystem.getAudioFileFormat(input);
                valid = AudioFileFormat.Type.WAVE.equals(format.getType());
            } catch (IOException | UnsupportedAudioFileException exception) {
                valid = false;
            }
        } else if (bytes.length >= 35 && "ogg".equals(extension)) {
            int segments = bytes[26] & 0xff;
            int payload = 27 + segments;
            valid = ascii(bytes, 0, 4).equals("OggS") && bytes[4] == 0 && segments > 0
                    && payload + 8 <= bytes.length
                    && (ascii(bytes, payload, 8).equals("OpusHead")
                    || (bytes[payload] == 1 && ascii(bytes, payload + 1, 6).equals("vorbis")));
        } else if (bytes.length >= 4 && "mp3".equals(extension)) {
            int offset = 0;
            if (bytes.length >= 10 && ascii(bytes, 0, 3).equals("ID3")) {
                if ((bytes[6] | bytes[7] | bytes[8] | bytes[9]) < 0) {
                    throw invalid();
                }
                offset = 10 + (bytes[6] << 21 | bytes[7] << 14 | bytes[8] << 7 | bytes[9]);
                if (bytes[3] == 4 && (bytes[5] & 0x10) != 0) {
                    offset += 10;
                }
            }
            if (offset + 4 <= bytes.length) {
                int version = (bytes[offset + 1] >> 3) & 3;
                int bitrateIndex = (bytes[offset + 2] >> 4) & 15;
                int rateIndex = (bytes[offset + 2] >> 2) & 3;
                valid = (bytes[offset] & 0xff) == 0xff && (bytes[offset + 1] & 0xe6) == 0xe2
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
                            + ((bytes[offset + 2] >> 1) & 1);
                    valid = offset + frameBytes <= bytes.length;
                }
            }
        }
        if (!valid) {
            throw invalid();
        }
    }

    /** 仅用于已检查长度的音频头，不解码用户文本。 */
    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    /** 歌词必须为 UTF-8 文本；JSON 行使用秒数 time 与纯文本 text。 */
    private void validateLyrics(byte[] bytes, String extension) {
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
        if ("lrc".equals(extension) && !Pattern.compile("\\[[0-9]{1,6}:[0-5][0-9](?:\\.[0-9]{1,3})?]")
                .matcher(text).find()) {
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

    /** 只信 ImageIO 识别结果，先读尺寸限制内存，再实际解码；声明的 Content-Type 不参与。 */
    private BufferedImage validateImage(Path path, String extension) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!("png".equals(extension) ? "png".equals(format) : "jpeg".equals(format))) {
                    throw invalid();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 25000000) {
                    throw invalid();
                }
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

    /** 检查文件操作权限，原 Token 和账号必须仍有效，ALL/SELF 只使用本次操作的授权。 */
    private Long requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:file:" + operation)) ? null : actor;
    }

    /** 检查文章查询范围，文件的 ALL 权限不能扩大文章的 SELF 范围。 */
    private Long articleScope() {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:article:query")) ? null : actor;
    }

    /** 整理文件响应，只返回允许的字段，不直接返回数据库实体。 */
    private FileVO view(BlogFile row) {
        return new FileVO(row.getFileId(), row.getUploaderId(), row.getOriginalName(), row.getMimeType(),
                row.getSizeBytes(), row.getStorageType(), row.getCreateTime(),
                "/api/v1/admin/files/" + row.getFileId() + "/content");
    }

    /** 检查文件是否存在，先校验权限和范围；文件不存在或无权访问时都返回 404。 */
    private BlogFile requireFile(BlogFile row) {
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /** 在持锁事务里读完文件，避免返回延迟流后状态已经变化。 */
    private FileContent content(BlogFile row) {
        return new FileContent(row.getOriginalName(), row.getMimeType(), storage.read(row));
    }

    /** 内容校验失败不回显文件内容、名称或解析器异常。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "文件名称、类型或内容不符合要求");
    }

    /** 总请求上限之外，每种类型仍执行自己的字节上限。 */
    private BusinessException tooLarge() {
        return new BusinessException(413, "PAYLOAD_TOO_LARGE", "图片限10MiB、音频限60MiB、歌词限1MiB");
    }

    /** 私密资源和不存在资源共用错误，避免透露其状态。 */
    private BusinessException missing() {
        return new BusinessException(404, "NOT_FOUND", "文件或引用内容不存在");
    }

    /** 存储故障让事务失败，不能返回空图片成功。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "文件存储暂不可用");
    }

    /** 批量删除文件，存储无法整批回滚，每个文件通过服务代理单独提交，并返回实际结果。 */
    @Override
    public BatchFileResultsVO deleteBatch(List<String> ids) {
        requireAdmin("delete");
        var results = new ArrayList<BatchFileResultVO>();
        BusinessException stopped = null;
        for (String value : ids) {
            long id = IdUtils.parse(value);
            try {
                if (stopped != null) {
                    throw stopped;
                }
                fileService.getObject().delete(id);
                results.add(new BatchFileResultVO(id, 200, "OK", "成功"));
            } catch (BusinessException error) {
                results.add(new BatchFileResultVO(id, error.getStatus(), error.getCode(), error.getMessage()));
                if (error.getStatus() == 401 || error.getStatus() == 403) {
                    stopped = error;
                }
            } catch (NotLoginException error) {
                stopped = new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
                results.add(new BatchFileResultVO(id, 401, stopped.getCode(), stopped.getMessage()));
            } catch (DataAccessException | TransactionException error) {
                results.add(new BatchFileResultVO(id, 503, "SERVICE_UNAVAILABLE", "文件服务暂不可用"));
            } catch (RuntimeException error) {
                results.add(new BatchFileResultVO(id, 500, "INTERNAL_ERROR", "文件清理失败"));
            }
        }
        return new BatchFileResultsVO(results);
    }

}
