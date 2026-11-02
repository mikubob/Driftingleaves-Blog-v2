package com.xuan.utils;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.exception.BusinessException;
import com.xuan.policy.UrlPolicy;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 访问路径工具，统计只保存站内路径，来源地址去掉查询参数和 # 片段。
 */
public final class SitePathUtils {

    private SitePathUtils() {
    }

    /** 检查站内路径，先去掉查询参数和 # 片段，再拒绝外部地址、反斜杠、控制字符和非法跳转路径。 */
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

    /** 保存访问来源时只保留协议、主机、端口和路径，不把登录票据或查询参数写入数据库。 */
    public static String referer(String value) {
        if (ObjectUtil.isNull(value)) {
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

    /** 返回路径错误，不带可能含凭据的原始输入。 */
    private static BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "访问路径或来源地址不符合要求");
    }
}
