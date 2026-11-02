package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 向账号当前邮箱发送后台登录验证码，不要求用户名和密码。 */
public record AdminEmailCodeDTO(@NotBlank @Email @Size(max = 254) String email) {

    public AdminEmailCodeDTO {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
