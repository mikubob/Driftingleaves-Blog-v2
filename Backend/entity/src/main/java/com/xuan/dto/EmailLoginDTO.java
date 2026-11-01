package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 邮箱登录仍须通过图形验证码，邮件中的六位数字保留前导零。 */
public record EmailLoginDTO(@NotBlank @Email @Size(max = 254) String email,
                            @NotNull @Pattern(regexp = "[0-9]{6}") String emailCode) {

    public EmailLoginDTO {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
