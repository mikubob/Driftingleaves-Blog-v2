package com.xuan.ratelimit;

import com.xuan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis 限流，登录和注解共用固定时间窗口，超限返回等待秒数，Redis 出错时拒绝请求。
 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local ttl = 0
            for i, key in ipairs(KEYS) do
                local n = redis.call('INCR', key)
                if n == 1 then redis.call('EXPIRE', key, ARGV[1]) end
                if n > tonumber(ARGV[i + 1]) then ttl = math.max(ttl, redis.call('TTL', key), 1) end
            end
            return ttl
            """, Long.class);
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local ttl = 0
            for _, key in ipairs(KEYS) do
                local n = tonumber(redis.call('GET', key) or '0')
                if n >= tonumber(ARGV[1]) then ttl = math.max(ttl, redis.call('TTL', key), 1) end
            end
            return ttl
            """, Long.class);
    private final StringRedisTemplate redis;

    /** 增加请求次数，用一次脚本更新各项计数，任一超限就拒绝。 */
    public void hit(List<String> keys, int windowSeconds, int limit) {
        hit(keys, windowSeconds, Collections.nCopies(keys.size(), limit));
    }

    /** 检查各项请求次数，IP、统计标识和用户可以分别设置上限。 */
    public void hit(List<String> keys, int windowSeconds, List<Integer> limits) {
        if (keys.isEmpty() || keys.size() != limits.size() || windowSeconds < 1 || limits.stream().anyMatch(n -> n < 1)) {
            throw new IllegalArgumentException("限流窗口和维度无效");
        }
        List<String> args = new ArrayList<>();
        args.add(Integer.toString(windowSeconds));
        limits.forEach(n -> args.add(n.toString()));
        reject(redis.execute(HIT, keys, args.toArray()));
    }

    /** 检查已有请求次数，不增加计数，供只统计失败次数的密码登录使用。 */
    public void check(List<String> keys, int limit) {
        reject(redis.execute(CHECK, keys, Integer.toString(limit)));
    }

    /** 处理限流结果，超限或 Redis 出错时抛出业务错误。 */
    private void reject(Long retryAfter) {
        if (retryAfter == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "请求保护暂不可用");
        }
        if (retryAfter > 0) {
            throw new BusinessException(429, "RATE_LIMITED", "请求过于频繁", Math.toIntExact(retryAfter));
        }
    }
}
