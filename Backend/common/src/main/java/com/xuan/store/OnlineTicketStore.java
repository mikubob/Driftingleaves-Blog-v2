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

/** 在线票据存储，使用专用键，核对来源、身份和完整数据后一次性删除。 */
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

    /** 生成在线票据，此时不增加在线人数，读取时不延长有效期。 */
    public String issue(String value) {
        String ticket = TokenUtils.random();
        redis.opsForValue().set(key(ticket), value, Duration.ofSeconds(30));
        return ticket;
    }

    /** 读取在线票据，拒绝无效格式，不访问其他 Redis 键或返回原票据。 */
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

    /** 一次性使用在线票据，并发请求只能成功一次，失败后不恢复票据。 */
    public void consume(String ticket, String expected) {
        if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key(ticket)), expected))) {
            throw invalid();
        }
    }

    /** 生成在线票据缓存键，只保存摘要，与其他票据分开。 */
    private String key(String ticket) {
        return auth.getRedisPrefix() + "online:ticket:" + TokenUtils.digest(ticket);
    }

    /** 返回票据无效错误，不透露具体原因。 */
    public static BusinessException invalid() {
        return new BusinessException(400, "ONLINE_TICKET_INVALID", "连接票据无效，请重新连接");
    }
}
