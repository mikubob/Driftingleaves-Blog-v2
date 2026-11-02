package com.xuan.interceptor;

import com.xuan.store.PublicCacheStore;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

/** 拦截数据库写入，在业务加锁和校验后、执行 SQL 前登记注解指定的缓存保护。 */
@Component
@RequiredArgsConstructor
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class PublicCacheMutationInterceptor implements Interceptor {
    private final PublicCacheStore store;

    /** 登记缓存保护后执行写入，登记失败时由原事务回滚。 */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        store.beforeMutation();
        return invocation.proceed();
    }
}
