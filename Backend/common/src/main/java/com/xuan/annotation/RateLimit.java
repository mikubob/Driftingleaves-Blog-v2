package com.xuan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** HTTP 接口固定窗口限流；IP 始终计数，按需同时限制当前用户或 Token。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 服务端稳定操作名；相同名称共享窗口。 */
    String value();
    /** 每个维度在窗口内允许的请求数。 */
    int limit();
    /** 窗口秒数。 */
    int windowSeconds() default 60;
    /** IP 之外的身份维度；USER/TOKEN 要求有效登录。 */
    Dimension dimension() default Dimension.IP;

    /** 可用的服务端身份维度，不读取客户端 userId。 */
    enum Dimension { IP, USER, TOKEN }
}
