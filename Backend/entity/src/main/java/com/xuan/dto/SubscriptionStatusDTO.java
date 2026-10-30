package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 管理员只能激活已验证邮箱或退订，不能写验证时间和邮箱。 */
public record SubscriptionStatusDTO(@NotNull @Min(1) @Max(2) Integer status) { }
