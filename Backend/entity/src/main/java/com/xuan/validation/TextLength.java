package com.xuan.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 长度按 Unicode 字符算，能不能传 null 由其他规则决定。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextLengthValidator.class)
public @interface TextLength {

    /** 最少字符数，按 Unicode 码点计数。 */
    int min() default 0;

    /** 最多字符数，按 Unicode 码点计数。 */
    int max();

    /** 文本长度校验失败时的提示。 */
    String message() default "字符数应在 {min} 到 {max} 之间";

    /** 指定在哪些校验组检查文本长度。 */
    Class<?>[] groups() default {};

    /** 为文本长度校验失败附加自定义信息。 */
    Class<? extends Payload>[] payload() default {};
}
