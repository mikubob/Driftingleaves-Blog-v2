package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.Setter;

/** 已有密码时须验证旧密码；无密码时必须省略该字段。 */
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
    /** 是否显式提交该字段。 */
    public boolean hasCurrentPassword() {
        return currentPasswordPresent;
    }
}
