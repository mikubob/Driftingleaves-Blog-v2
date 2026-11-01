package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 后台先核对账密和账号已有邮箱，不能给任意地址发送登录验证码。 */
public record AdminEmailCodeDTO(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,64}") String username,
                                @NotNull @Password String password,
                                @NotBlank @Email @Size(max = 254) String email) {

    public AdminEmailCodeDTO {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
