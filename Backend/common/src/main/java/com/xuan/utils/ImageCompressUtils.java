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

        float initialQuality = (float) properties.getQuality();
        byte[] result = encode(image, scale, outputFormat, initialQuality);
        boolean supportsLossy = "webp".equals(outputFormat) || "jpeg".equals(outputFormat);
        if (supportsLossy && result.length > targetBytes && initialQuality > 0.3f) {
            // 先试最低质量 0.3；若最低质量仍超限则直接取较小结果，否则在 [0.3, initialQuality] 二分查找最高可用质量。
            float low = 0.3f;
            float high = initialQuality;
            byte[] lowest = encode(image, scale, outputFormat, low);
            if (lowest.length > targetBytes) {
                if (lowest.length < result.length) {
                    result = lowest;
                }
            } else {
                result = lowest;
                for (int step = 0; step < 5 && (high - low) >= 0.04f; step++) {
                    float mid = (low + high) / 2.0f;
                    byte[] candidate = encode(image, scale, outputFormat, mid);
                    if (candidate.length <= targetBytes) {
                        result = candidate;
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
            }
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
