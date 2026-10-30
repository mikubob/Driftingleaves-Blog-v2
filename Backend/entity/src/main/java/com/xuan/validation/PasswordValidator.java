package com.xuan.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * 检查完整密码长度，超过 BCrypt 字节上限就拒绝，不能让它悄悄截断。
 */
public class PasswordValidator implements ConstraintValidator<Password, String> {

    /**
     * 同时检查 Unicode 字符数和 UTF-8 字节数，null 留给必填校验。
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 1. null 由字段的 @NotNull 检查，这里只检查密码长度。
        if (value == null) {
            return true;
        }

        // 2. 字符数和 UTF-8 字节数都要查，避免 BCrypt 悄悄截断密码。
        int length = value.codePointCount(0, value.length());
        return length >= 8 && length <= 64 && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
