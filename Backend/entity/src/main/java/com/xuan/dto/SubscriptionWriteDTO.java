package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import java.util.Locale;

/** 邮箱由本站用户主动选择，规范化后仍必须重新收信确认。 */
public record SubscriptionWriteDTO(@NotNull @Email @TextLength(min = 1, max = 254) String email) {
    public SubscriptionWriteDTO { email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
}
