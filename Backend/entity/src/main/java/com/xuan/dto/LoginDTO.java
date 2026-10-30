package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 密码登录只收用户名，不能拿邮箱当登录名。
 */
public record LoginDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,64}") String username,
                       @NotNull @Password String password) {

    public LoginDTO {
        username = username == null ? null : username.strip();
    }
}
