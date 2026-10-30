package com.xuan.utils;

import com.xuan.exception.BusinessException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 访问统计只保存站内规范路径与去掉查询/fragment的来源URL。
 */
public final class SitePathUtils {

    private SitePathUtils() {
    }

    /** 先剔除query和fragment，再拒绝外部地址、反斜杠、控制字符与绕过路径。 */
    public static String path(String value) {
        try {
            URI uri = new URI(value);
            String path = uri.getPath();
            if (uri.isAbsolute() || uri.getRawAuthority() != null || path == null || !path.startsWith(
                    "/") || path.startsWith("//")
                    || path.contains("\\") || path.contains("%") || path.contains("?") || path.contains(
                    "#") || path.codePoints().anyMatch(Character::isISOControl)
                    || !new URI(null, null, path, null).normalize().getPath().equals(path) || path.contains("//")) {
                throw new IllegalArgumentException();
            }
            return path;
        } catch (URISyntaxException | IllegalArgumentException error) {
            throw invalid();
        }
    }

    /** 来源只保留协议、主机、端口与路径，不把登录票据或访问参数落库。 */
    public static String referer(String value) {
        if (value == null) {
            return null;
        }
        if (!UrlPolicy.valid(value)) {
            throw invalid();
        }
        try {
            URI uri = new URI(value);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null,
                    null).toASCIIString();
        } catch (URISyntaxException error) {
            throw invalid();
        }
    }

    /** 不回显可能含凭据的输入。 */
    private static BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "访问路径或来源地址不符合要求");
    }
}
