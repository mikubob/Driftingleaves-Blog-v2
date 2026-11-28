package com.xuan.aspect;

import com.xuan.annotation.PublicCacheRead;
import com.xuan.cache.PublicCacheContext;
import com.xuan.constant.CacheNames;
import com.xuan.store.PublicCacheStore;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 在事务第一次查询前记录缓存调用信息，内部通过代理调用时共用同一个缓存版本。
 */
@Aspect
@Component
@Order(80)
@RequiredArgsConstructor
public class PublicCacheAspect {
    private static final SpelExpressionParser EXPRESSIONS = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETERS = new DefaultParameterNameDiscoverer();
    private final PublicCacheStore store;

    /**
     * 处理公开查询缓存，只缓存已声明的方法，不缓存私有业务或通用 Mapper。
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable) || " +
            "@annotation(org.springframework.cache.annotation.CacheEvict) || @annotation(org.springframework.cache.annotation.Caching) || " +
            "@annotation(com.xuan.annotation.ArticleCacheEvict) || @annotation(com.xuan.annotation.PublicCacheRead)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 1. 解析目标方法上的公开缓存失效注解（含组合注解）并计算 SpEL 条件，轻量缓存域不参与公开写保护。
        var method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        Set<String> evictions = new LinkedHashSet<>();
        CacheEvict single = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);
        Caching multiple = AnnotatedElementUtils.findMergedAnnotation(method, Caching.class);
        var arguments = new MethodBasedEvaluationContext(null, method, point.getArgs(), PARAMETERS);
        if (single != null && isPublicManager(single.cacheManager()) && applies(single, arguments)) {
            evictions.addAll(Arrays.asList(single.cacheNames()));
        }
        if (multiple != null) {
            for (CacheEvict evict : multiple.evict()) {
                if (isPublicManager(evict.cacheManager()) && applies(evict, arguments)) {
                    evictions.addAll(Arrays.asList(evict.cacheNames()));
                }
            }
        }

        // 2. 打开当前层缓存上下文（同时为 JSON 反序列化提供方法返回类型），仅对提前查库的 @PublicCacheRead 预登记起始版本。
        PublicCacheContext.open(method.getGenericReturnType(), evictions);
        try {
            PublicCacheRead entry = method.getAnnotation(PublicCacheRead.class);
            if (entry != null) {
                for (String domain : entry.value()) {
                    store.beginRead(domain);
                }
            }
            return point.proceed();
        } finally {
            // 3. 无论成功或异常都关闭当前层上下文，避免线程复用串扰。
            PublicCacheContext.close();
        }
    }

    /**
     * 判断注解是否使用默认公开缓存管理器，轻量缓存管理器不接入 PublicCacheStore 写保护。
     */
    private boolean isPublicManager(String cacheManager) {
        return !CacheNames.SIMPLE_CACHE_MANAGER.equals(cacheManager);
    }

    /**
     * 按方法入参检查失效条件，写保护与 CacheEvict 使用同一份条件。
     */
    private boolean applies(CacheEvict rule, EvaluationContext arguments) {
        return rule.condition().isEmpty()
                || Boolean.TRUE.equals(EXPRESSIONS.parseExpression(rule.condition()).getValue(arguments, Boolean.class));
    }
}
