package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 清除密码时要提供当前密码，用户身份从会话取，不能传目标用户 ID。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordClearDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 当前密码
    @NotNull
    @Password
    private String currentPassword;
}
