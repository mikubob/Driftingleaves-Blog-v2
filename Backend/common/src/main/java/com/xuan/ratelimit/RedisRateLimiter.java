package com.xuan.ratelimit;

import com.xuan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

/** 认证和接口注解共用的 Redis 原子固定窗口；故障关闭并统一返回等待秒数。 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {
    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local ttl = 0
            for _, key in ipairs(KEYS) do
                local n = redis.call('INCR', key)
                if n == 1 then redis.call('EXPIRE', key, ARGV[1]) end
                if n > tonumber(ARGV[2]) then ttl = math.max(ttl, redis.call('TTL', key), 1) end
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

    /** 所有维度在同一脚本内累计一次，超过任一维度限制则拒绝。 */
    public void hit(List<String> keys, int windowSeconds, int limit) {
        reject(redis.execute(HIT, keys, Integer.toString(windowSeconds), Integer.toString(limit)));
    }

    /** 只检查已累计次数，供仅记录失败的密码登录复用。 */
    public void check(List<String> keys, int limit) {
        reject(redis.execute(CHECK, keys, Integer.toString(limit)));
    }

    /** 将脚本结果转换为统一业务错误，禁止把 Redis 保护故障视为放行。 */
    private void reject(Long retryAfter) {
        if (retryAfter == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "请求保护暂不可用");
        }
        if (retryAfter > 0) {
            throw new BusinessException(429, "RATE_LIMITED", "请求过于频繁", Math.toIntExact(retryAfter));
        }
    }
}
