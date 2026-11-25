package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 密码登录只收用户名，不能拿邮箱当登录名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDTO implements Serializable {

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

    public void setUsername(String username) {
        this.username = username == null ? null : username.strip();
    }
}
