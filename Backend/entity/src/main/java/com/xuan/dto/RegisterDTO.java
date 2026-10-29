package com.xuan.dto;

import com.xuan.validation.Password;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 公开注册白名单；角色和启用状态由服务端确定。 */
public record RegisterDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,64}") String username,
                          @NotNull @Password String password,
                          @NotNull @TextLength(min = 1, max = 64) String nickname) {
    public RegisterDTO {
        username = username == null ? null : username.strip();
        nickname = nickname == null ? null : nickname.strip();
    }
}
