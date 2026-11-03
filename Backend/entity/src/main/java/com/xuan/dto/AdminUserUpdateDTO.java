package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;

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

    @AssertTrue(message = "邮箱、头像或简介未完整提交，请重新打开用户编辑窗口后重试。")
    public boolean isCompleteReplace() {
        return emailPresent && avatarPresent && introPresent;
    }
}
