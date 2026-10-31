package com.xuan;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 不替换 SQL 或业务结果，只统计真实查询，并在确定位置控制并发次序。 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class CacheQueryProbe implements Interceptor {
    final ConcurrentMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    volatile String pauseStatement;
    volatile CountDownLatch reached = new CountDownLatch(0);
    volatile CountDownLatch release = new CountDownLatch(0);
    static class Gate {
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
    }
    final ConcurrentLinkedQueue<Gate> gates = new ConcurrentLinkedQueue<>();
    void pause(String statement) {
        var gate = pauseTwo(statement).get(0);
        gates.removeIf(value -> value != gate);
        reached = gate.reached; release = gate.release;
    }
    java.util.List<Gate> pauseTwo(String statement) {
        pauseStatement = statement; gates.clear();
        var values = java.util.List.of(new Gate(), new Gate()); gates.addAll(values); return values;
    }
    void reset() {
        release.countDown(); gates.forEach(gate -> gate.release.countDown());
        gates.clear(); calls.clear(); pauseStatement = null;
    }
    int count(String statement) { return calls.getOrDefault(statement, new AtomicInteger()).get(); }
    @Override public Object intercept(Invocation invocation) throws Throwable {
        String statement = ((MappedStatement) invocation.getArgs()[0]).getId();
        calls.computeIfAbsent(statement, key -> new AtomicInteger()).incrementAndGet();
        Object result = invocation.proceed();
        if (statement.equals(pauseStatement)) {
            Gate gate = gates.poll();
            if (gate != null) {
                gate.reached.countDown();
                if (!gate.release.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("并发测试未释放 SQL"); }
            }
        }
        return result;
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean CacheQueryProbe cacheQueryProbe() { return new CacheQueryProbe(); }
    }
}
