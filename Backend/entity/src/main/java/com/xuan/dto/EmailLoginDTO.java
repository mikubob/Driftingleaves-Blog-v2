package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * 邮箱登录仍须通过图形验证码，邮件中的六位数字保留前导零。
 */
@Data
@NoArgsConstructor
public class EmailLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 电子邮箱
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    // 邮箱验证码
    @NotNull
    @Pattern(regexp = "[0-9]{6}", message = "须为6位数字，请填写邮件中的验证码")
    private String emailCode;

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
