package com.xuan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按固定时间窗口限流，IP 一直计数，需要时再加上当前用户或 Token。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 同名操作共用一个限流窗口，名称由服务端确定。 */
    String value();

    /** 每个维度在窗口内允许的请求数。 */
    int limit();

    /** 窗口秒数。 */
    int windowSeconds() default 60;

    /** 除了 IP 还要检查哪种身份；用 USER 或 TOKEN 时必须先登录。 */
    Dimension dimension() default Dimension.IP;

    /**
     * 身份从服务端会话里取，不读客户端传来的 userId。
     */
    enum Dimension {
        IP, USER, TOKEN
    }
}
