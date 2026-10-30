package com.xuan.dto;

import jakarta.validation.constraints.NotNull;

/** 操作只能选枚举里的值，要绑定谁从原本站会话里取。 */
public record OAuthAuthorizeDTO(@NotNull Operation operation) {
    public enum Operation { LOGIN, BIND }
}
