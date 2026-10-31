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

/** 在线票据独立命名；核对来源和身份后比较完整载荷，原子删除。 */
@Component
@RequiredArgsConstructor
public class OnlineTicketStore {
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('DEL',KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuthProperties auth;

    /** 申请不登记在线状态，TTL 不随读取续期。 */
    public String issue(String value) {
        String ticket = TokenUtils.random();
        redis.opsForValue().set(key(ticket), value, Duration.ofSeconds(30));
        return ticket;
    }

    /** 错误票据不访问任意 Redis 键，也不回显凭据。 */
    public String read(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        String value = redis.opsForValue().get(key(ticket));
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    /** 并发加入只有一个消费者成功；失败后不能恢复票据。 */
    public void consume(String ticket, String expected) {
        if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key(ticket)), expected))) {
            throw invalid();
        }
    }

    /** 键里只留票据摘要，在线与其他用途的票据不能混用。 */
    private String key(String ticket) {
        return auth.getRedisPrefix() + "online:ticket:" + TokenUtils.digest(ticket);
    }

    /** 统一拒绝无效票据，不泄露具体失效原因。 */
    public static BusinessException invalid() {
        return new BusinessException(400, "ONLINE_TICKET_INVALID", "连接票据无效，请重新连接");
    }
}
