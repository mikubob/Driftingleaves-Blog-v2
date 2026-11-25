package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邮件退订只能操作相同邮箱、验证时间和本轮订阅。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUnsubscribe implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 订阅ID
    private long subscriptionId;

    // 订阅邮箱
    private String email;

    // 验证时间
    private LocalDateTime verifiedAt;

    // 订阅时间
    private LocalDateTime subscribeTime;

    // 本轮订阅标识
    private String round;
}
