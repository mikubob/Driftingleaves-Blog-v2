package com.xuan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 写事务审计声明；仅提取显式数字 ID，不序列化方法参数或返回对象。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /** 服务端固定操作类型。 */
    String type();
    /** 服务端固定资源名。 */
    String target();
    /** 业务实际检查的权限码快照。 */
    String permission();
    /** 数字目标 ID 在方法参数中的位置；创建时从明确的返回类型提取。 */
    int targetIdArgument() default -1;
}
