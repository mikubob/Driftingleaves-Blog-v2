package com.xuan.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 密码按 Unicode 字符计数，不去空白、不截断，还要满足 BCrypt 的字节限制。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface Password {

    /** 密码长度或字节数校验失败时的提示。 */
    String message() default "密码须为8..64个字符且UTF-8不超过72字节";

    /** 指定在哪些校验组检查密码。 */
    Class<?>[] groups() default {};

    /** 为密码校验失败附加自定义信息。 */
    Class<? extends Payload>[] payload() default {};
}
