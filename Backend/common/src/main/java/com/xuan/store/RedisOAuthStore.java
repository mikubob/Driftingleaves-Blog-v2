package com.xuan.store;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.cache.AuthStateCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

/** 分离本站一次性流程与 JustAuth state；compare-and-delete 由 Redis 单次 Lua 完成。 */
@Component
@RequiredArgsConstructor
public class RedisOAuthStore implements AuthStateCache {
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public void save(String kind, String secret, String value, Duration ttl) {
        redis.opsForValue().set(key(kind, secret), value, ttl);
    }

    public String read(String kind, String secret) {
        // 1. 在访问 Redis 前检查外部票据格式，不接受任意键片段。
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        // 2. 只读取未过期记录，过期、重放和格式错误统一为流程失效。
        String value = redis.opsForValue().get(key(kind, secret));
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    /** 先由调用方验证完整浏览器/操作/原会话关系，错误关联不会删除合法流程。 */
    public void consume(String kind, String secret, String expected) {
        // 1. 将完整记录比较和删除放入一次 Lua 执行，只有一个并发请求能成功消费。
        Long consumed = redis.execute(CONSUME, List.of(key(kind, secret)), expected);
        // 2. 记录变化、已过期或已被消费均拒绝，不恢复旧值。
        if (!Long.valueOf(1).equals(consumed)) {
            throw invalid();
        }
    }

    public void deleteState(String state) {
        redis.delete(key("justauth", state));
    }

    @Override
    public void cache(String key, String value) {
        cache(key, value, 300_000);
    }

    @Override
    public void cache(String key, String value, long timeout) {
        // JustAuth 的 timeout 单位是毫秒，不能按秒写入 Redis TTL。
        save("justauth", key, value, Duration.ofMillis(timeout));
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key("justauth", key));
    }

    @Override
    public boolean containsKey(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key("justauth", key)));
    }

    private String key(String kind, String secret) {
        return properties.getRedisPrefix() + "oauth:" + kind + ":" + TokenUtils.digest(secret);
    }

    public static BusinessException invalid() {
        return new BusinessException(400, "OAUTH_STATE_INVALID", "授权流程已失效，请重新发起");
    }
}
