package com.xuan.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 按 Unicode 字符计数，避免一个表情被 UTF-16 算成两个字符。
 */
public class TextLengthValidator implements ConstraintValidator<TextLength, String> {

    private int min;
    private int max;

    /**
     * 保存字段声明的最小和最大字符数。
     */
    @Override
    public void initialize(TextLength constraint) {
        min = constraint.min();
        max = constraint.max();
    }

    /**
     * 按 Unicode 码点检查长度，null 留给单独的必填校验。
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 1. 这里只查长度，是否必填交给单独的校验。
        if (value == null) {
            return true;
        }

        // 2. 按 Unicode 码点计数，表情这类字符也和数据库按同样方式计算。
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}
