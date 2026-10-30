package com.xuan.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 密码按 Unicode 字符计数，不去空白、不截断，还要满足 BCrypt 的字节限制。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface Password {
    String message() default "密码须为8..64个字符且UTF-8不超过72字节";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
