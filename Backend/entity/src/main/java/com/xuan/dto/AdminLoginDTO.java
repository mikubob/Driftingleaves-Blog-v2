package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 后台登录同时提交账密、已绑定邮箱及邮件验证码。 */
public record AdminLoginDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,64}") String username,
                            @NotNull @Password String password,
                            @NotBlank @Email @Size(max = 254) String email,
                            @NotNull @Pattern(regexp = "[0-9]{6}") String emailCode) {

    public AdminLoginDTO {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
