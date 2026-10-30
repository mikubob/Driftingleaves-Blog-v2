package com.xuan.utils;

import com.xuan.exception.BusinessException;
import java.net.URI;
import java.net.URISyntaxException;

/** 展示 URL 不触发抓取，仅接受无凭据、无控制字符的绝对 HTTP(S) 地址。 */
public final class UrlPolicy {
    private UrlPolicy() { }

    /**
     * 校验公开展示地址，不抓取远程资源，也不允许附件 API 路径用于公开展示。
     * @param value 待校验地址；null 交由字段必填约束处理
     * @return 是否为长度和结构合法的绝对 HTTP(S) 地址，null 返回 true
     */
    public static boolean valid(String value) {
        // 1. 空值交由字段的可空约束决定，这里只校验实际提供的地址。
        if (value == null) {
            return true;
        }
        // 2. 先限制字符长度并排除控制字符，再按 URI 结构检查协议、主机和用户信息。
        if (value.codePointCount(0, value.length()) > 2048
                || value.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            // 展示字段不能使用附件 API；先解码并归一化路径，等价绝对 URL 也须拒绝。
            String path = new URI(null, null, uri.getPath(), null).normalize().getPath();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && !path.matches("/api/v1/(blog|admin)/(articles/[^/]+/)?files(/.*)?");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /**
     * 校验头像地址，无效时保留统一业务错误，不包含被拒绝的输入内容。
     * @param value 待校验头像地址，可为 null
     * @throws BusinessException 地址无效时返回 VALIDATION_ERROR
     */
    public static void requireValid(String value) {
        if (!valid(value)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "avatar须为合法的公开HTTP(S)地址");
        }
    }
}
