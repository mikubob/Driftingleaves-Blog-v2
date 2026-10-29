package com.xuan.service;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

/** 认证入口双维度 Redis 固定窗口限流；仅信任指定直连代理的客户端地址。 */
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {
    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local ttl = 0
            for _, key in ipairs(KEYS) do
                local n = redis.call('INCR', key)
                if n == 1 then redis.call('EXPIRE', key, ARGV[1]) end
                if n > tonumber(ARGV[2]) then ttl = math.max(ttl, redis.call('TTL', key)) end
            end
            return ttl
            """, Long.class);
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local ttl = 0
            for _, key in ipairs(KEYS) do
                local n = tonumber(redis.call('GET', key) or '0')
                if n >= tonumber(ARGV[1]) then ttl = math.max(ttl, redis.call('TTL', key)) end
            end
            return ttl
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public void checkLogin(HttpServletRequest request, String username) {
        reject(redis.execute(CHECK, keys("login", request, username.toLowerCase(Locale.ROOT)),
                Integer.toString(properties.getRate().getLoginFailures())));
    }

    public void loginFailed(HttpServletRequest request, String username) {
        hit("login", request, username.toLowerCase(Locale.ROOT), properties.getRate().getLoginFailures());
    }

    public void register(HttpServletRequest request, String browser) {
        hit("register", request, browser, properties.getRate().getRegister());
    }

    public void oauth(HttpServletRequest request, String browser) {
        hit("oauth", request, browser, properties.getRate().getOauth());
    }

    private void hit(String operation, HttpServletRequest request, String identity, int limit) {
        reject(redis.execute(HIT, keys(operation, request, identity),
                Integer.toString(properties.getRate().getWindowSeconds()), Integer.toString(limit)));
    }

    private List<String> keys(String operation, HttpServletRequest request, String identity) {
        // 1. 默认使用直连地址，仅在直连方属于受信代理时读取转发的客户端 IP。
        String ip = request.getRemoteAddr();
        if (properties.getTrustedProxies().contains(ip)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                ip = forwarded.split(",")[0].strip();
            }
        }
        // 2. 分别构建 IP 与身份维度的键，身份缺失时退为 IP，摘要避免暴露原值。
        String prefix = properties.getRedisPrefix() + "rate:" + operation + ":";
        String dimension = identity == null ? ip : identity;
        return List.of(prefix + "ip:" + SessionService.digest(ip),
                prefix + "identity:" + SessionService.digest("login".equals(operation) ? ip + ":" + dimension : dimension));
    }

    private void reject(Long retryAfter) {
        // 1. Redis 未返回可靠结果时拒绝操作，不能将保护故障当成未超限。
        if (retryAfter == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "认证保护暂不可用");
        }
        // 2. 超限时携带剩余等待秒数，由 HTTP 层输出 Retry-After。
        if (retryAfter > 0) {
            throw new BusinessException(429, "RATE_LIMITED", "请求过于频繁", Math.toIntExact(retryAfter));
        }
    }
}
