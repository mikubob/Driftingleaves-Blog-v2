package com.xuan.dto;

import com.xuan.validation.Password;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * 公开注册只收这些字段，角色和启用状态由服务端决定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户名
    @NotNull
    @Pattern(regexp = "[A-Za-z0-9_]{3,64}", message = "须为3至64位字母、数字或下划线")
    private String username;

    // 密码
    @NotNull
    @Password
    private String password;

    // 昵称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String nickname;

    // 电子邮箱
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    // 邮箱验证码
    @NotNull
    @Pattern(regexp = "[0-9]{6}", message = "须为6位数字，请填写邮件中的验证码")
    private String emailCode;

    public void setUsername(String username) {
        this.username = username == null ? null : username.strip();
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? null : nickname.strip();
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
