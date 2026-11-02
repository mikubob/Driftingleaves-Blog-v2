package com.xuan.utils;

import com.xuan.properties.ImageProperties;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** 复用已经校验、解码的图片压缩，避免重复解码和逐轮叠加有损压缩。 */
public final class ImageCompressUtils {

    private ImageCompressUtils() {
    }

    /** 保留原格式和 PNG 透明通道；只有产物更小时才采用，否则返回原字节。 */
    public static byte[] compress(byte[] original, BufferedImage image, String extension,
            ImageProperties properties) throws IOException {
        long targetBytes = properties.getMaxSizeKb() * 1024L;
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (!properties.isEnabled()
                || (original.length <= targetBytes && longest <= properties.getMaxDimension())) {
            return original;
        }

        double scale = Math.min(1.0, (double) properties.getMaxDimension() / longest);
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        boolean png = "png".equals(extension);
        int imageType = png && image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage source = new BufferedImage(width, height, imageType);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        float quality = (float) properties.getQuality();
        byte[] result = encode(source, png ? "png" : "jpeg", quality);
        // PNG 降质量不会有损减小体积；JPEG 每轮都编码同一像素源，到最低质量就停止。
        while (!png && result.length > targetBytes && quality > 0.3f) {
            quality = Math.max(0.3f, quality - 0.1f);
            byte[] candidate = encode(source, "jpeg", quality);
            if (candidate.length < result.length) {
                result = candidate;
            }
        }
        return result.length < original.length ? result : original;
    }

    /** 写入内存并及时释放编码器，不把临时图片留给 ImageIO 磁盘缓存。 */
    private static byte[] encode(BufferedImage image, String format, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if ("jpeg".equals(format)) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
