package com.xuan.policy;

import com.xuan.exception.BusinessException;

/**
 * 校验上传用途与文件类型是否匹配，未指定用途时按文件类型选择默认值。
 */
public final class FileUploadUsagePolicy {

    private FileUploadUsagePolicy() {
    }

    /**
     * 返回可保存的用途，拒绝未知用途和类型不匹配的用途。
     */
    public static String resolve(String usage, String mimeType) {
        String defaultUsage;
        if (mimeType.startsWith("image/")) {
            defaultUsage = "IMAGE";
        } else if (mimeType.startsWith("audio/")) {
            defaultUsage = "SONG";
        } else if ("text/plain".equals(mimeType) || "application/json".equals(mimeType)) {
            defaultUsage = "LYRIC";
        } else {
            defaultUsage = "ATTACHMENT";
        }
        if (usage == null) {
            return defaultUsage;
        }
        boolean allowed = switch (usage) {
            case "ARTICLE_COVER", "ARTICLE_IMAGE", "MUSIC_COVER", "AVATAR", "LINK_AVATAR",
                 "EXPERIENCE_LOGO", "ICON" -> "IMAGE".equals(defaultUsage);
            default -> defaultUsage.equals(usage);
        };
        if (!allowed) {
            throw new BusinessException(400, "VALIDATION_ERROR", "上传用途无效或与文件类型不符。");
        }
        return usage;
    }
}
