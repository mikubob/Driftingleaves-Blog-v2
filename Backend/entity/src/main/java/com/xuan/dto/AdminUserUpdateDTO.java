package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改资料要填全，可空字段也不能漏；用户名、密码和角色不在这里修改。
 */
@Getter
public class AdminUserUpdateDTO {

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
    @Setter
    private Integer status;

    @Getter(AccessLevel.NONE)
    private boolean emailPresent;

    @Getter(AccessLevel.NONE)
    private boolean avatarPresent;

    @Getter(AccessLevel.NONE)
    private boolean introPresent;

    public void setNickname(String value) {
        nickname = value == null ? null : value.strip();
    }

    public void setEmail(String value) {
        emailPresent = true;
        email = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public void setAvatar(String value) {
        avatarPresent = true;
        avatar = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public void setIntro(String value) {
        introPresent = true;
        intro = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    @AssertTrue(message = "email、avatar和intro必须显式提供，可为null")
    public boolean isCompleteReplace() {
        return emailPresent && avatarPresent && introPresent;
    }
}
