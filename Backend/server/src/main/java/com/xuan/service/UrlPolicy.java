package com.xuan.service;

import com.xuan.exception.BusinessException;
import java.net.URI;
import java.net.URISyntaxException;

/** 展示 URL 不触发抓取，仅接受无凭据、无控制字符的绝对 HTTP(S) 地址。 */
public final class UrlPolicy {
    private UrlPolicy() { }

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
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    public static void requireValid(String value) {
        if (!valid(value)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "avatar须为合法的公开HTTP(S)地址");
        }
    }
}
