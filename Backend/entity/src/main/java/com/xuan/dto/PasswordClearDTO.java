package com.xuan.dto;

import com.xuan.validation.Password;
import jakarta.validation.constraints.NotNull;

/** 清除密码须提交当前密码，不接受目标用户 ID。 */
public record PasswordClearDTO(@NotNull @Password String currentPassword) { }
