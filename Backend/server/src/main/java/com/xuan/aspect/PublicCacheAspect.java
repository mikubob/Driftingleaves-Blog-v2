package com.xuan.aspect;

import com.xuan.annotation.PublicCacheRead;
import com.xuan.cache.PublicCacheContext;
import com.xuan.store.PublicCacheStore;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** 在事务第一次查询前记录缓存调用信息，内部通过代理调用时共用同一个缓存版本。 */
@Aspect
@Component
@Order(80)
@RequiredArgsConstructor
public class PublicCacheAspect {
    private static final SpelExpressionParser EXPRESSIONS = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETERS = new DefaultParameterNameDiscoverer();
    private final PublicCacheStore store;

    /** 处理公开查询缓存，只缓存已声明的方法，不缓存私有业务或通用 Mapper。 */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable) || " +
            "@annotation(org.springframework.cache.annotation.CacheEvict) || @annotation(org.springframework.cache.annotation.Caching) || " +
            "@annotation(com.xuan.annotation.PublicCacheRead)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        var method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        Set<String> evictions = new LinkedHashSet<>();
        CacheEvict single = method.getAnnotation(CacheEvict.class);
        Caching multiple = method.getAnnotation(Caching.class);
        var arguments = new MethodBasedEvaluationContext(null, method, point.getArgs(), PARAMETERS);
        if (single != null && applies(single, arguments)) {
            evictions.addAll(Arrays.asList(single.cacheNames()));
        }
        if (multiple != null) {
            for (CacheEvict evict : multiple.evict()) {
                if (applies(evict, arguments)) {
                    evictions.addAll(Arrays.asList(evict.cacheNames()));
                }
            }
        }
        PublicCacheContext.open(method.getGenericReturnType(), evictions);
        try {
            Set<String> reads = new LinkedHashSet<>();
            var read = method.getAnnotation(Cacheable.class);
            if (read != null) {
                reads.addAll(Arrays.asList(read.cacheNames()));
            }
            if (multiple != null) {
                for (var rule : multiple.cacheable()) {
                    reads.addAll(Arrays.asList(rule.cacheNames()));
                }
            }
            PublicCacheRead entry = method.getAnnotation(PublicCacheRead.class);
            if (entry != null) {
                reads.addAll(Arrays.asList(entry.value()));
            }
            reads.forEach(store::beginRead);
            return point.proceed();
        } finally {
            PublicCacheContext.close();
        }
    }

    /** 按方法入参检查失效条件，写保护与 CacheEvict 使用同一份条件。 */
    private boolean applies(CacheEvict rule, EvaluationContext arguments) {
        return rule.condition().isEmpty()
                || Boolean.TRUE.equals(EXPRESSIONS.parseExpression(rule.condition()).getValue(arguments, Boolean.class));
    }
}
