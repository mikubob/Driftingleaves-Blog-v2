package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 随机结果票据仅用于一次兑换，不是本站登录凭据。 */
public record OAuthExchangeDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String ticket) { }
