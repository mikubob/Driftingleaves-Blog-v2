package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;

/**
 * 只接受JSON票据，不接受邮箱、用户ID或URL查询替代凭据。
 */
public record SubscriptionTicketDTO(@NotNull @TextLength(min = 1, max = 512) String ticket) {

}
