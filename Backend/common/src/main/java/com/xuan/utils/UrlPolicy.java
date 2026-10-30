package com.xuan.utils;

import com.xuan.exception.BusinessException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

/** 这里只检查展示 URL，不抓取资源；只收不带凭据和控制字符的绝对 HTTP(S) 地址。 */
public final class UrlPolicy {
    /** 与当前前端约定使用的图标键一致，其他图标可直接使用外部URL。 */
    private static final Set<String> ICONS = Set.of("document", "setting", "home", "chart", "folder", "tag", "chat",
            "link", "picture", "music", "mail", "user", "lock", "menu", "key", "github");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");
    private UrlPolicy() { }

    /** 展示图标只能是已登记键或外部HTTP(S)地址。 */
    public static boolean validIcon(String value) { return value == null || ICONS.contains(value) || valid(value); }

    /** 社交邮箱链接只收单个邮箱，不收查询参数、fragment或控制字符。 */
    public static boolean validSocial(String value) {
        if (value != null && value.startsWith("mailto:")) {
            String email = value.substring(7);
            return email.length() <= 254 && EMAIL.matcher(email).matches()
                    && email.indexOf('?') < 0 && email.indexOf('#') < 0;
        }
        return valid(value);
    }

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
