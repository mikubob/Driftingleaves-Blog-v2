package com.xuan.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 随机标识和 SHA-256 摘要工具，不依赖会话、Redis、业务模型或应用配置。 */
public final class TokenUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() { }

    /**
     * 生成 256 bit 随机值，用于 Token、浏览器标识、state 和 ticket。
     * @return 无填充的 URL 安全 Base64 字符串，长度固定为 43
     */
    public static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 按 UTF-8 计算不可逆摘要，用于业务版本及敏感标识的 Redis 键和关联。
     * @param value 待摘要文本，不可为 null
     * @return 64 位小写十六进制 SHA-256
     * @throws IllegalStateException JDK 缺少 SHA-256 实现时拒绝降级
     */
    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256", exception);
        }
    }
}
