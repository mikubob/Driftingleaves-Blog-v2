package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 只在Redis保存本次申请数据，不进入HTTP响应或日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionConfirmation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户ID
    private long userId;

    // 订阅ID
    private long subscriptionId;

    // 订阅邮箱
    private String email;

    // 本轮确认标识
    private String round;
}
