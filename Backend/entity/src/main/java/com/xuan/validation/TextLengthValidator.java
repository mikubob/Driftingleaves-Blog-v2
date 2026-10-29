package com.xuan.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 避免 UTF-16 代理对被算作两个数据库字符。 */
public class TextLengthValidator implements ConstraintValidator<TextLength, String> {
    private int min;
    private int max;

    @Override
    public void initialize(TextLength constraint) {
        min = constraint.min();
        max = constraint.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 1. 可空性由独立约束决定，长度校验不重复承担必填检查。
        if (value == null) {
            return true;
        }
        // 2. 按 Unicode 码点计数，使补充平面字符与数据库字符长度一致。
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}
