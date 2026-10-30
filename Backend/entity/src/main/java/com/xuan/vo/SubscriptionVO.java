package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 本人订阅响应不返回内部轮次、票据或所属用户字段。
 */
public record SubscriptionVO(Long subscriptionId, String email, Integer status, LocalDateTime verifiedAt,
                             LocalDateTime subscribeTime, LocalDateTime unsubscribeTime, LocalDateTime createTime,
                             LocalDateTime updateTime) {

}
