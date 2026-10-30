package com.xuan.model;

import java.time.LocalDateTime;

/**
 * 邮件退订只能操作相同邮箱、验证时间和本轮订阅。
 */
public record SubscriptionUnsubscribe(long subscriptionId, String email, LocalDateTime verifiedAt,
                                      LocalDateTime subscribeTime, String round) {

}
