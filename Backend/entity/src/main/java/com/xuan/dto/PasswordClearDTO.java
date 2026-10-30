package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;

/**
 * 清除密码时要提供当前密码，用户身份从会话取，不能传目标用户 ID。
 */
public record PasswordClearDTO(@NotNull @Password String currentPassword) {

}
