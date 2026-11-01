package com.xuan.store;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 验证码使用独立 Redis 键，任何一次校验尝试都不能复用。 */
@Component
@RequiredArgsConstructor
public class CaptchaStore {

    public static final int EXPIRES_IN = 120;
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local code = redis.call('GET', KEYS[1])
            redis.call('DEL', KEYS[1])
            return code
            """, String.class);
    private final StringRedisTemplate redis;
    private final AuthProperties auth;

    /** 答案只写入短期缓存，不返回给浏览器。 */
    public String issue(String code) {
        String captchaId = TokenUtils.random();
        redis.opsForValue().set(key(captchaId), code, Duration.ofSeconds(EXPIRES_IN));
        return captchaId;
    }

    /** 先原子取出并删除，再比较答案；错误和并发请求同样消耗验证码。 */
    public void verify(String captchaId, String code) {
        if (captchaId == null || !captchaId.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        String expected = redis.execute(CONSUME, List.of(key(captchaId)));
        if (expected == null || !expected.equals(code)) {
            throw invalid();
        }
    }

    private String key(String captchaId) {
        return auth.getRedisPrefix() + "captcha:" + TokenUtils.digest(captchaId);
    }

    private BusinessException invalid() {
        return new BusinessException(400, "CAPTCHA_INVALID", "验证码错误或已失效，请重新获取");
    }
}
