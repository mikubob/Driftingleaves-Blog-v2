package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 指定接收换绑验证码的新邮箱。 */
public record EmailChangeCodeDTO(@NotBlank @Email @Size(max = 254) String email) {

    public EmailChangeCodeDTO {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
