package com.xuan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 给写事务声明操作日志，目标 ID 来自明确参数或创建结果，业务字段由切面保存。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作类型由服务端写死。 */
    String type();

    /** 资源名称由服务端写死。 */
    String target();

    /** 记录业务实际检查过的权限码，不用它来授予权限。 */
    String permission();

    /** 这里指定目标 ID 是第几个参数；创建操作从已知返回类型里取 ID。 */
    int targetIdArgument() default -1;
}
