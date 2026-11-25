package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * 邮箱验证码只按服务端允许的用途发送，不能跨用途登录或注册。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCodeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 电子邮箱
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    // 验证码用途
    @NotNull
    private Purpose purpose;

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 前台只接受注册或登录用途，不能请求后台验证码。
     */
    public enum Purpose {
        REGISTER, LOGIN
    }
}
