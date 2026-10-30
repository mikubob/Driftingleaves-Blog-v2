package com.xuan.utils;

import com.xuan.exception.BusinessException;
import java.net.URI;
import java.net.URISyntaxException;

/** 这里只检查展示 URL，不抓取资源；只收不带凭据和控制字符的绝对 HTTP(S) 地址。 */
public final class UrlPolicy {
    private UrlPolicy() { }

    /**
     * 检查公开展示地址，不访问远端，也不能把附件 API 路径当公开图片地址。
     * @param value 待校验地址；null 交由字段必填约束处理
     * @return 是否为长度和结构合法的绝对 HTTP(S) 地址，null 返回 true
     */
    public static boolean valid(String value) {
        // 1. 有没有必填由字段决定，这里只检查实际传进来的地址。
        if (value == null) {
            return true;
        }
        // 2. 先检查长度和控制字符，再检查协议、主机和用户信息。
        if (value.codePointCount(0, value.length()) > 2048
                || value.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            // 展示字段不能用附件 API；先解码和整理路径，换成等价绝对地址也要拒绝。
            String path = new URI(null, null, uri.getPath(), null).normalize().getPath();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && !path.matches("/api/v1/(blog|admin)/(articles/[^/]+/)?files(/.*)?");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /**
     * 头像地址不合法就报统一业务错误，不把原地址写进错误里。
     * @param value 待校验头像地址，可为 null
     * @throws BusinessException 地址无效时返回 VALIDATION_ERROR
     */
    public static void requireValid(String value) {
        if (!valid(value)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "avatar须为合法的公开HTTP(S)地址");
        }
    }
}
