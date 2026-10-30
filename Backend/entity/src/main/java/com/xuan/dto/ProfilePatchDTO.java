package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * setter 记下字段有没有出现，才能分清没传、传 null 和传空串。
 */
@Getter
public class ProfilePatchDTO {

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
    private boolean nicknamePresent;

    @Getter(AccessLevel.NONE)
    private boolean emailPresent;

    @Getter(AccessLevel.NONE)
    private boolean avatarPresent;

    @Getter(AccessLevel.NONE)
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

    @AssertTrue(message = "至少提供一项资料，nickname不能为null")
    public boolean isValidPatch() {
        return (nicknamePresent || emailPresent || avatarPresent || introPresent)
                && (!nicknamePresent || nickname != null);
    }

    private String normalize(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /** 记下客户端有没有传这个字段。 */
    public boolean hasNickname() {
        return nicknamePresent;
    }

    /** 记下客户端有没有传这个字段。 */
    public boolean hasEmail() {
        return emailPresent;
    }

    /** 记下客户端有没有传这个字段。 */
    public boolean hasAvatar() {
        return avatarPresent;
    }

    /** 记下客户端有没有传这个字段。 */
    public boolean hasIntro() {
        return introPresent;
    }
}
