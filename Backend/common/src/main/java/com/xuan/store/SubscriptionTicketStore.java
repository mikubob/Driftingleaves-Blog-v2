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
 * 邮件票据存储，使用专用键，确认票据只能用一次，退订票据可重复读取。
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

    /** 生成订阅确认票据，持有用户和订阅锁时更新申请轮次，旧票据立即失效。 */
    public void issue(String ticket, long user, String round, String value) {
        redis.execute(ISSUE, List.of(key("confirm", ticket), roundKey(user)), value, round);
    }

    /** 读取订阅确认票据，不立即删除，身份、邮箱和当前状态在加锁事务中检查。 */
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

    /** 一次性使用订阅确认票据，完整数据和申请轮次匹配后才删除，不能消耗其他账号的票据。 */
    public void consume(String ticket, long user, String round, String expected) {
        if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key("confirm", ticket), roundKey(user)), expected, round))) {
            throw invalid();
        }
    }

    /** 生成新的退订轮次标识，另存随机值，避免同一毫秒重新订阅时旧链接又生效。 */
    public void newDeliveryRound(long subscription) {
        redis.opsForValue().set(deliveryKey(subscription), TokenUtils.random());
    }

    /** 获取当前退订轮次，缺失时拒绝旧链接，不根据时间推算。 */
    public String deliveryRound(long subscription) {
        return redis.opsForValue().get(deliveryKey(subscription));
    }

    /** 生成邮件退订票据，只在发信时创建，仅用于对应订阅的退订。 */
    public String unsubscribeTicket(String value) {
        String ticket = TokenUtils.random();
        redis.opsForValue().set(key("unsubscribe", ticket), value, Duration.ofDays(30));
        return ticket;
    }

    /** 生成票据缓存键，只使用摘要，不保存原票据。 */
    private String key(String kind, String ticket) {
        return properties.getRedisPrefix() + "subscription:" + kind + ":" + TokenUtils.digest(ticket);
    }

    /** 生成申请轮次缓存键，每个用户只保留当前轮次。 */
    private String roundKey(long user) {
        return properties.getRedisPrefix() + "subscription:application:" + user;
    }

    /** 生成退订轮次缓存键，该轮次不用于登录认证。 */
    private String deliveryKey(long subscription) {
        return properties.getRedisPrefix() + "subscription:delivery:" + subscription;
    }

    /** 返回邮件票据无效错误，不透露账号、邮箱或过期原因。 */
    public static BusinessException invalid() {
        return new BusinessException(400, "SUBSCRIPTION_TICKET_INVALID", "订阅票据无效，请重新申请");
    }
}
