package com.xuan.dto;

import jakarta.validation.constraints.NotNull;

/** 操作类型由枚举约束，目标绑定身份来自原本站会话。 */
public record OAuthAuthorizeDTO(@NotNull Operation operation) {
    public enum Operation { LOGIN, BIND }
}
