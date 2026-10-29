package com.xuan.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 数据库字符长度按 Unicode 码点校验，可空性由其他约束决定。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextLengthValidator.class)
public @interface TextLength {
    int min() default 0;
    int max();
    String message() default "字符长度不符合要求";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
