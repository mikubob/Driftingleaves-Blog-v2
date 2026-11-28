package com.xuan.policy;

import com.xuan.exception.BusinessException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 展示地址校验，只接受不含凭据和控制字符的完整 HTTP(S) 地址，不抓取内容。
 */
public final class UrlPolicy {

    /**
     * 与当前前端约定使用的图标键一致，其他图标可直接使用外部URL。
     */
    private static final Set<String> ICONS = Set.of("document", "setting", "home", "chart", "folder", "tag", "chat",
            "link", "picture", "music", "mail", "user", "lock", "menu", "key", "github");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9]" +
                    "(?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");
    private static final Pattern ATTACHMENT_API_PATH = Pattern.compile(
            "/api/v1/(blog|admin)/(articles/[^/]+/|music/)?files(?:[/;].*)?");

    private UrlPolicy() {
    }

    /**
     * 检查图标，只允许已注册名称或外部 HTTP(S) 地址。
     */
    public static boolean validIcon(String value) {
        return value == null || ICONS.contains(value) || valid(value);
    }

    /**
     * 检查社交链接中的邮箱地址，只允许单个邮箱，不接受查询参数、# 片段或控制字符。
     */
    public static boolean validSocial(String value) {
        if (value != null && value.startsWith("mailto:")) {
            String email = value.substring(7);
            return email.length() <= 254 && EMAIL.matcher(email).matches()
                    && email.indexOf('?') < 0 && email.indexOf('#') < 0;
        }
        return valid(value);
    }

    /**
     * 检查展示链接，不访问远端，附件接口路径不能当作公开图片地址。
     *
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
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null) {
                return false;
            }
            // 与 FileUrlPolicy 对齐路径规范化：迭代解码、反斜杠归一、路径折叠并忽略大小写与矩阵参数，阻止附件接口伪装成公开地址。
            String rawPath = uri.getRawPath();
            if (rawPath == null || rawPath.isEmpty()) {
                return true;
            }
            String decoded = rawPath;
            String previous;
            do {
                previous = decoded;
                decoded = URLDecoder.decode(decoded.replace("+", "%2B"), StandardCharsets.UTF_8);
            } while (!decoded.equals(previous) && decoded.contains("%"));
            String slashNormalized = decoded.replace('\\', '/');
            String normalized = URI.create("/" + slashNormalized.replaceFirst("^/+", "")).normalize().getPath();
            String lowerNormalized = normalized.toLowerCase(Locale.ROOT);
            String lowerOriginal = slashNormalized.toLowerCase(Locale.ROOT);
            return !ATTACHMENT_API_PATH.matcher(lowerNormalized).matches()
                    && !ATTACHMENT_API_PATH.matcher(lowerOriginal).matches();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 检查头像地址，不合法时抛出业务错误，不返回原地址。
     *
     * @param value 待校验头像地址，可为 null
     * @throws BusinessException 地址无效时返回 VALIDATION_ERROR
     */
    public static void requireValid(String value) {
        if (!valid(value)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "头像地址无效，请填写以 http:// 或 https:// 开头的公开图片地址。");
        }
    }
}
