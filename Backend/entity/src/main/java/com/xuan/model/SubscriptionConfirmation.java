package com.xuan.model;

/**
 * 只在Redis保存本次申请载荷，不进入HTTP响应或日志。
 */
public record SubscriptionConfirmation(long userId, long subscriptionId, String email, String round) {

}
