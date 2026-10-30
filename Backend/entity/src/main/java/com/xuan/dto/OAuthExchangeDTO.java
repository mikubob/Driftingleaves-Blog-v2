package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 结果票据只能兑换一次，不能直接拿它当本站登录 Token。 */
public record OAuthExchangeDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String ticket) { }
