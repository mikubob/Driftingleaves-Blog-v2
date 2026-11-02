package com.xuan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 在业务事务查询前记录缓存版本，供内部代理调用的缓存方法共用。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicCacheRead {
    /** 本次业务查询及内部缓存方法需要共用版本的缓存名称。 */
    String[] value();
}
