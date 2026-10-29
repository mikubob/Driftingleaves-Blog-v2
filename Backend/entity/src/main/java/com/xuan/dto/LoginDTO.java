package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 密码登录仅接受用户名，不以邮箱识别身份。 */
public record LoginDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,64}") String username,
                       @NotNull @Password String password) {
    public LoginDTO {
        username = username == null ? null : username.strip();
    }
}
