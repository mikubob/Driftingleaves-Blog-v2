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

/** 图形验证码存储，使用独立 Redis 键，每次校验后作废。 */
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

    /** 生成验证码并短期保存答案，不把答案返回浏览器。 */
    public String issue(String code) {
        String captchaId = TokenUtils.random();
        redis.opsForValue().set(key(captchaId), code, Duration.ofSeconds(EXPIRES_IN));
        return captchaId;
    }

    /** 校验图形验证码，先一次性取出并删除，再比较答案，输错或并发请求也不能重复使用。 */
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
