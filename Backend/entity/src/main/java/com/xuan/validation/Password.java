package com.xuan.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** BCrypt 输入边界，字符数按 Unicode 码点计算，不 trim 或截断。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface Password {
    String message() default "密码须为8..64个字符且UTF-8不超过72字节";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
