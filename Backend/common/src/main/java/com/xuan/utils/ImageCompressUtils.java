package com.xuan.utils;

import com.xuan.properties.ImageProperties;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 图片压缩与转码工具，使用已解码的原图，避免重复解码和多次有损压缩。
 */
public final class ImageCompressUtils {

    private ImageCompressUtils() {
    }

    /**
     * 压缩并转码图片；开启后全部转为配置的输出格式（默认 webp），超限时逐步降低质量重试。
     */
    public static byte[] compress(byte[] original, BufferedImage image, String extension,
            ImageProperties properties) throws IOException {
        if (!properties.isEnabled()) {
            return original;
        }

        String outputFormat = properties.getOutputFormat().toLowerCase(Locale.ROOT);
        long targetBytes = properties.getMaxSizeKb() * 1024L;
        int longest = Math.max(image.getWidth(), image.getHeight());
        double scale = longest > properties.getMaxDimension()
                ? (double) properties.getMaxDimension() / longest : 1.0;

        float quality = (float) properties.getQuality();
        byte[] result = encode(image, scale, outputFormat, quality);
        boolean supportsLossy = "webp".equals(outputFormat) || "jpeg".equals(outputFormat);
        int attempts = 0;
        while (supportsLossy && result.length > targetBytes && quality > 0.3f && attempts < 10) {
            quality = Math.max(0.3f, quality - 0.05f);
            byte[] candidate = encode(image, scale, outputFormat, quality);
            if (candidate.length < result.length) {
                result = candidate;
            }
            attempts++;
        }

        // 只有输入本来就是目标格式且未超边长、转码后反而更大时，才保留原字节。
        boolean sameFormat = outputFormat.equals(extension)
                || ("jpeg".equals(outputFormat) && "jpg".equals(extension));
        if (sameFormat && scale == 1.0 && result.length >= original.length) {
            return original;
        }
        return result;
    }

    /**
     * 使用 Thumbnailator 按目标格式和质量编码到内存，强制标准色彩类型防止 WebP 偏色。
     */
    private static byte[] encode(BufferedImage image, double scale, String outputFormat,
            float quality) throws IOException {
        int imageType = ("png".equals(outputFormat) || "webp".equals(outputFormat))
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Thumbnails.of(image)
                    .scale(scale)
                    .imageType(imageType)
                    .outputFormat(outputFormat)
                    .outputQuality(quality)
                    .toOutputStream(output);
            return output.toByteArray();
        }
    }
}
