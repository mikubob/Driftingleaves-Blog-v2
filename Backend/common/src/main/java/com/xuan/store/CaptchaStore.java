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

/** 验证码存储，图形与算术使用独立 Redis 键，每次校验后作废。 */
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
        return issue(code, "");
    }

    /** 校验图形验证码，先一次性取出并删除，再比较答案，输错或并发请求也不能重复使用。 */
    public void verify(String captchaId, String code) {
        verify(captchaId, code, "");
    }

    /** 保存算术题答案，与登录图形验证码隔离，不能跨用途提交。 */
    public String issueArithmetic(int answer) {
        return issue(Integer.toString(answer), "math:");
    }

    /** 校验算术题答案，正确、错误和重放都不能再次使用同一题。 */
    public void verifyArithmetic(String captchaId, String answer) {
        verify(captchaId, answer, "math:");
    }

    /** 按用途短期保存验证码答案。 */
    private String issue(String code, String purpose) {
        String captchaId = TokenUtils.random();
        redis.opsForValue().set(key(captchaId, purpose), code, Duration.ofSeconds(EXPIRES_IN));
        return captchaId;
    }

    /** 一次性取出当前用途的答案，再比较用户输入。 */
    private void verify(String captchaId, String code, String purpose) {
        if (captchaId == null || !captchaId.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        String expected = redis.execute(CONSUME, List.of(key(captchaId, purpose)));
        if (expected == null || !expected.equals(code)) {
            throw invalid();
        }
    }

    /** 生成用途隔离的验证码缓存键。 */
    private String key(String captchaId, String purpose) {
        return auth.getRedisPrefix() + "captcha:" + purpose + TokenUtils.digest(captchaId);
    }

    /** 返回统一的验证码错误，不区分过期和答案错误。 */
    private BusinessException invalid() {
        return new BusinessException(400, "CAPTCHA_INVALID", "验证码错误或已失效，请重新获取");
    }
}
