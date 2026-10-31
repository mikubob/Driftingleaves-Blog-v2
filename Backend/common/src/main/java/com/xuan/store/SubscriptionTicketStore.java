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

/**
 * 邮件票据单独命名，确认原子消费，退订票据只读以支持幂等。
 */
@Component
@RequiredArgsConstructor
public class SubscriptionTicketStore {
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            redis.call('SET',KEYS[1],ARGV[1],'EX',86400)
            redis.call('SET',KEYS[2],ARGV[2],'EX',86400)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[1]) ~= ARGV[1] or redis.call('GET',KEYS[2]) ~= ARGV[2] then return 0 end
            redis.call('DEL',KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    /** 持有用户和订阅锁时替换当前申请轮次，旧票据即刻不能确认。 */
    public void issue(String ticket, long user, String round, String value) {
        redis.execute(ISSUE, List.of(key("confirm", ticket), roundKey(user)), value, round);
    }

    /** 票据读取不消费；身份、邮箱和当前状态由锁事务核验。 */
    public String read(String kind, String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        String value = redis.opsForValue().get(key(kind, ticket));
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    /** 比较完整载荷与当前申请轮次后删除，不能把错误账号的票据消费掉。 */
    public void consume(String ticket, long user, String round, String expected) {
        if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key("confirm", ticket), roundKey(user)), expected, round))) {
            throw invalid();
        }
    }

    /** 退订轮次另存随机值，避免同一毫秒重订让旧链接重新有效。 */
    public void newDeliveryRound(long subscription) {
        redis.opsForValue().set(deliveryKey(subscription), TokenUtils.random());
    }

    /** 轮次缺失就拒绝旧链接，不能用时间猜一个轮次。 */
    public String deliveryRound(long subscription) {
        return redis.opsForValue().get(deliveryKey(subscription));
    }

    /** 只在真正构造发出邮件时生成作用域受限的退订票据。 */
    public String unsubscribeTicket(String value) {
        String ticket = TokenUtils.random();
        redis.opsForValue().set(key("unsubscribe", ticket), value, Duration.ofDays(30));
        return ticket;
    }

    /** 键使用摘要，不保留原票据。 */
    private String key(String kind, String ticket) {
        return properties.getRedisPrefix() + "subscription:" + kind + ":" + TokenUtils.digest(ticket);
    }

    /** 每个用户只有一个当前申请轮次。 */
    private String roundKey(long user) {
        return properties.getRedisPrefix() + "subscription:application:" + user;
    }

    /** Redis轮次不参与认证或本站会话。 */
    private String deliveryKey(long subscription) {
        return properties.getRedisPrefix() + "subscription:delivery:" + subscription;
    }

    /** 所有无效票据使用同一个错误，不泄露账号、邮箱或过期原因。 */
    public static BusinessException invalid() {
        return new BusinessException(400, "SUBSCRIPTION_TICKET_INVALID", "订阅票据无效，请重新申请");
    }
}
