package com.xuan.dto;

import com.xuan.validation.Password;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 后台创建用户只收这些字段，默认启用，角色要走单独的分配接口。
 */
@Data
@NoArgsConstructor
public class AdminUserCreateDTO implements Serializable {

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
    @Email
    @TextLength(max = 254)
    private String email;

    // 头像URL
    @TextLength(max = 2048)
    private String avatar;

    // 个人简介
    @TextLength(max = 500)
    private String intro;

    // 状态
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status = 1;

    public void setUsername(String value) {
        username = value == null ? null : value.strip();
    }

    public void setNickname(String value) {
        nickname = value == null ? null : value.strip();
    }

    public void setEmail(String value) {
        email = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public void setAvatar(String value) {
        avatar = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public void setIntro(String value) {
        intro = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public void setStatus(Integer value) {
        status = value;
    }
}
