package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
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
 * 已有密码时要验证旧密码；原来没密码时不能传这个字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSetDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 新密码
    @NotNull
    @Password
    private String newPassword;

    // 当前密码
    @Password
    private String currentPassword;

    // 是否传入当前密码
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean currentPasswordPresent;

    public void setCurrentPassword(String value) {
        currentPasswordPresent = true;
        currentPassword = value;
    }

    /**
     * 记下客户端有没有传这个字段。
     */
    public boolean hasCurrentPassword() {
        return currentPasswordPresent;
    }
}
