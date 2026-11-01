package com.xuan.store;

import com.xuan.client.MailClient;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** 邮件验证码独立保存五分钟，按用途和邮箱原子消费，答案不返回浏览器。 */
@Component
@RequiredArgsConstructor
public class EmailCodeStore {

    public static final String REGISTER = "register";
    public static final String LOGIN = "login";
    public static final String ADMIN_LOGIN = "admin-login";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local code = redis.call('GET', KEYS[1])
            redis.call('DEL', KEYS[1])
            return code
            """, String.class);
    private final StringRedisTemplate redis;
    private final AuthProperties auth;
    private final MailClient mail;

    /** 先保存摘要再同步发送，SMTP 失败时删除本次验证码并向上传播错误。 */
    public void send(String email, String purpose) {
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String key = key(email, purpose);
        redis.opsForValue().set(key, TokenUtils.digest(code), Duration.ofMinutes(5));
        try {
            String action = switch (purpose) {
                case REGISTER -> "注册账号";
                case LOGIN -> "邮箱登录";
                case ADMIN_LOGIN -> "后台登录";
                default -> throw new IllegalArgumentException("未知邮件验证码用途");
            };
            mail.send(email, "Drifting Leaves - " + action + "验证码",
                    "您正在" + action + "，验证码：" + code + "。五分钟内有效，请勿向他人透露。若非本人操作，请忽略。后台验证码仅用于后台登录。");
        } catch (RuntimeException exception) {
            redis.delete(key);
            throw exception;
        }
    }

    /** 每次尝试都取出并删除，输错、过期、重放和跨用途使用均拒绝。 */
    public void verify(String email, String purpose, String code) {
        String expected = redis.execute(CONSUME, List.of(key(email, purpose)));
        if (code == null || expected == null || !expected.equals(TokenUtils.digest(code))) {
            throw new BusinessException(400, "EMAIL_CODE_INVALID", "邮箱验证码错误或已失效，请重新发送");
        }
    }

    /** 邮箱大小写和首尾空白统一处理，Redis 键中不保存原地址。 */
    private String key(String email, String purpose) {
        return auth.getRedisPrefix() + "email-code:" + purpose + ":"
                + TokenUtils.digest(email.strip().toLowerCase(Locale.ROOT));
    }
}
