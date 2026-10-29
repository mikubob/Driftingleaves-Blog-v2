package com.xuan.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** 校验密码的完整输入，拒绝 BCrypt 会静默截断的字节长度。 */
public class PasswordValidator implements ConstraintValidator<Password, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 1. null 交由请求字段的 @NotNull 处理，这里只负责密码本身的长度边界。
        if (value == null) {
            return true;
        }
        // 2. 分别检查 Unicode 字符数和 UTF-8 字节数，避免 BCrypt 静默截断。
        int length = value.codePointCount(0, value.length());
        return length >= 8 && length <= 64 && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
