package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * 向账号当前邮箱发送后台登录验证码，不要求用户名和密码。
 */
@Data
@NoArgsConstructor
public class AdminEmailCodeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 电子邮箱
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
