package com.xuan.dto;

import com.xuan.validation.Password;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 公开注册只收这些字段，角色和启用状态由服务端决定。
 */
public record RegisterDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,64}") String username,
                          @NotNull @Password String password,
                          @NotNull @TextLength(min = 1, max = 64) String nickname,
                          @NotBlank @Email @Size(max = 254) String email,
                          @NotNull @Pattern(regexp = "[0-9]{6}") String emailCode) {

    public RegisterDTO {
        username = username == null ? null : username.strip();
        nickname = nickname == null ? null : nickname.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
