package com.xuan.ratelimit;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** 404 限流，反复访问不存在的地址时临时封禁客户端 IP，不使用客户端自报的统计标识。 */
@Component
@RequiredArgsConstructor
public class NotFoundRateLimiter {

    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local remaining = redis.call('TTL', KEYS[2])
            if remaining ~= -2 then return math.max(remaining, 1) end
            if ARGV[1] == 'record' then
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
                if count > 20 then
                    redis.call('SET', KEYS[2], '1', 'EX', 86400)
                    return 86400
                end
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    /** 检查 IP 是否被封禁，访问不延长原封禁时间。 */
    public void check(String ip) {
        execute(ip, "check");
    }

    /** 记录 404 次数，前后端共用计数，第 21 次通过脚本一次性设置一天封禁。 */
    public void record(String ip) {
        execute(ip, "record");
    }

    /** 执行封禁检查脚本，不保存完整 IP，Redis 故障时拒绝请求。 */
    private void execute(String ip, String operation) {
        if (ip == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "无法确认访问地址");
        }
        String prefix = properties.getRedisPrefix() + "abuse:ip:" + TokenUtils.digest(ip);
        Long remaining;
        try {
            remaining = redis.execute(CHECK, List.of(prefix + ":404", prefix + ":blocked"), operation);
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "请求保护暂不可用");
        }
        if (remaining == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "请求保护暂不可用");
        }
        if (remaining > 0) {
            throw new BusinessException(403, "ACCESS_BLOCKED", "访问暂时受限", Math.toIntExact(remaining));
        }
    }
}
