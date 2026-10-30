package com.xuan.dto;

import com.xuan.validation.Password;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台创建用户只收这些字段，默认启用，角色要走单独的分配接口。
 */
@Getter
public class AdminUserCreateDTO {

    @NotNull
    @Pattern(regexp = "[A-Za-z0-9_]{3,64}")
    private String username;

    @NotNull
    @Password
    @Setter
    private String password;

    @NotNull
    @TextLength(min = 1, max = 64)
    private String nickname;

    @Email
    @TextLength(max = 254)
    private String email;

    @TextLength(max = 2048)
    private String avatar;

    @TextLength(max = 500)
    private String intro;

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
