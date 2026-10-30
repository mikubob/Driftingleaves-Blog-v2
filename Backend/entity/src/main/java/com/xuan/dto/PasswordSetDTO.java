package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 已有密码时要验证旧密码；原来没密码时不能传这个字段。
 */
@Getter
public class PasswordSetDTO {

    @NotNull
    @Password
    @Setter
    private String newPassword;

    @Password
    private String currentPassword;

    @Getter(AccessLevel.NONE)
    private boolean currentPasswordPresent;

    public void setCurrentPassword(String value) {
        currentPasswordPresent = true;
        currentPassword = value;
    }

    /** 记下客户端有没有传这个字段。 */
    public boolean hasCurrentPassword() {
        return currentPasswordPresent;
    }
}
