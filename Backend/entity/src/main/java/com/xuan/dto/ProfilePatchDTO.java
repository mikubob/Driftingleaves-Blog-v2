package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * setter 记下字段有没有出现，才能分清没传、传 null 和传空串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfilePatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 昵称
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

    // 是否传入昵称
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean nicknamePresent;

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
        nicknamePresent = true;
        nickname = value == null ? null : value.strip();
    }

    public void setEmail(String value) {
        emailPresent = true;
        email = normalize(value);
    }

    public void setAvatar(String value) {
        avatarPresent = true;
        avatar = normalize(value);
    }

    public void setIntro(String value) {
        introPresent = true;
        intro = normalize(value);
    }

    /**
     * 检查资料修改请求至少包含一个字段，传入昵称时不能为 null。
     */
    @AssertTrue(message = "请至少修改一项个人资料，昵称不能为空。")
    public boolean isValidPatch() {
        return (nicknamePresent || emailPresent || avatarPresent || introPresent)
                && (!nicknamePresent || nickname != null);
    }

    /**
     * 去掉可空资料的首尾空白，空白内容按清空处理。
     */
    private String normalize(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /**
     * 记下客户端有没有传这个字段。
     */
    public boolean hasNickname() {
        return nicknamePresent;
    }

    /**
     * 记下客户端有没有传这个字段。
     */
    public boolean hasEmail() {
        return emailPresent;
    }

    /**
     * 记下客户端有没有传这个字段。
     */
    public boolean hasAvatar() {
        return avatarPresent;
    }

    /**
     * 记下客户端有没有传这个字段。
     */
    public boolean hasIntro() {
        return introPresent;
    }
}
