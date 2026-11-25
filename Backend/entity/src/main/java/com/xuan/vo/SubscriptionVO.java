package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本人订阅响应不返回内部轮次、票据或所属用户字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 订阅ID
    private Long subscriptionId;

    // 电子邮箱
    private String email;

    // 状态
    private Integer status;

    // 验证时间
    private LocalDateTime verifiedAt;

    // 订阅时间
    private LocalDateTime subscribeTime;

    // 退订时间
    private LocalDateTime unsubscribeTime;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
