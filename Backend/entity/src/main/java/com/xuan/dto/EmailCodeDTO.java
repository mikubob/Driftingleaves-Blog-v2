package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 邮箱验证码只按服务端允许的用途发送，不能跨用途登录或注册。 */
public record EmailCodeDTO(@NotBlank @Email @Size(max = 254) String email,
                           @NotNull Purpose purpose) {

    public EmailCodeDTO {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** 前台只接受注册或登录用途，不能请求后台验证码。 */
    public enum Purpose {
        REGISTER, LOGIN
    }
}
