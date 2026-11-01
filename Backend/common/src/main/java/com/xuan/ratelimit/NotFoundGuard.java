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

/** 反复访问不存在地址时临时封禁可信 IP，不使用客户端自报的统计指纹。 */
@Component
@RequiredArgsConstructor
public class NotFoundGuard {

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

    /** 业务执行前检查封禁；访问不会延长原来的封禁时间。 */
    public void check(String ip) {
        execute(ip, "check");
    }

    /** 后端 404 与前端未知路由共享计数，第 21 次原子写入一天的封禁。 */
    public void record(String ip) {
        execute(ip, "record");
    }

    /** 不保存完整 IP，Redis 故障时拒绝请求，不能跳过访问保护。 */
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
