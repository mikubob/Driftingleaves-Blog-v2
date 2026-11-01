package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.util.Locale;

/**
 * 邮箱必须与当前账号邮箱一致，规范化后仍须收信确认。
 */
public record SubscriptionWriteDTO(@NotNull @Email @TextLength(min = 1, max = 254) String email) {

    public SubscriptionWriteDTO {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
