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

/** 本站一次性流程和 JustAuth state 分开存，用一次 Redis Lua 完成比较和删除。 */
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
        // 1. 先检查票据格式，再访问 Redis，不能让外部输入随意拼接键。
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        // 2. 只取没过期的记录，格式不对、过期或重复使用都报流程失效。
        String value = redis.opsForValue().get(key(kind, secret));
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    /** 调用方先核对浏览器、操作和原会话，关联不对时不能删掉别人的合法流程。 */
    public void consume(String kind, String secret, String expected) {
        // 1. 在同一次 Lua 里比较完整记录并删除，并发请求只能有一个成功。
        Long consumed = redis.execute(CONSUME, List.of(key(kind, secret)), expected);
        // 2. 记录改了、过期了或已经用过就拒绝，不能把旧记录放回去。
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
        // JustAuth 的 timeout 单位是毫秒，写 Redis TTL 时别当成秒。
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
