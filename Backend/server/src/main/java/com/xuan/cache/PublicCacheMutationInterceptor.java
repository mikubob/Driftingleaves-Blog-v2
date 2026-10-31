package com.xuan.cache;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

/** 在原业务锁与校验之后、SQL 修改之前启动对应注解声明的缓存保护。 */
@Component
@RequiredArgsConstructor
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class PublicCacheMutationInterceptor implements Interceptor {
    private final PublicCacheStore store;

    /** 登记成功才执行真实写 SQL，登记失败仍交给原事务回滚。 */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        store.beforeMutation();
        return invocation.proceed();
    }
}
