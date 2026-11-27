package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改资料要填全，可空字段也不能漏；用户名、密码和角色不在这里修改。
 */
@Data
@NoArgsConstructor
public class AdminUserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 昵称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String nickname;

    // 电子邮箱
    @Email
    @TextLength(max = 254)
    private String email;

    // 新邮箱验证码
    @Pattern(regexp = "[0-9]{6}", message = "须为6位数字，请填写新邮箱中的验证码")
    private String emailCode;

    // 头像URL
    @TextLength(max = 2048)
    private String avatar;

    // 个人简介
    @TextLength(max = 500)
    private String intro;

    // 是否传入邮箱
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean emailPresent;

    // 是否传入头像
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean avatarPresent;

    // 是否传入个人简介
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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

    /**
     * 检查邮箱、头像和简介是否都已提交。
     */
    @AssertTrue(message = "邮箱、头像或简介未完整提交，请重新打开用户编辑窗口后重试。")
    public boolean isCompleteReplace() {
        return emailPresent && avatarPresent && introPresent;
    }
}
