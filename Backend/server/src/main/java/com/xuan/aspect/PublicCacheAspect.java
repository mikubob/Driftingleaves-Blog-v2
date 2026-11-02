package com.xuan.aspect;

import com.xuan.cache.PublicCacheContext;
import com.xuan.service.impl.ArticleServiceImpl;
import com.xuan.service.impl.SiteConfigServiceImpl;
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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 在事务第一次查询前记录缓存调用信息，内部通过代理调用时共用同一个缓存版本。 */
@Aspect
@Component
@Order(80)
@RequiredArgsConstructor
public class PublicCacheAspect {
    private final PublicCacheStore store;

    /** 处理公开查询缓存，只缓存已声明的方法，不缓存私有业务或通用 Mapper。 */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable) || " +
            "@annotation(org.springframework.cache.annotation.CacheEvict) || @annotation(org.springframework.cache.annotation.Caching) || " +
            "execution(* com.xuan.service.impl.ArticleServiceImpl.publicArticles(..)) || " +
            "execution(* com.xuan.service.impl.ArticleServiceImpl.publicDetail(..)) || " +
            "execution(* com.xuan.service.impl.CommentServiceImpl.publicComments(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        var method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        Set<String> evictions = new LinkedHashSet<>();
        CacheEvict single = method.getAnnotation(CacheEvict.class);
        Caching multiple = method.getAnnotation(Caching.class);
        if (single != null) {
            evictions.addAll(Arrays.asList(single.cacheNames()));
        }
        if (multiple != null) {
            for (CacheEvict evict : multiple.evict()) {
                evictions.addAll(Arrays.asList(evict.cacheNames()));
            }
        }
        // 只有 site 的公开资料依赖缓存，审核开关仍实时读写。
        if (point.getTarget() instanceof SiteConfigServiceImpl &&
                point.getArgs().length > 0 && !"site".equals(point.getArgs()[0])) {
            evictions.clear();
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
            if (method.getName().equals("publicArticles")) {
                reads.addAll(List.of("articles", "search"));
            }
            if (method.getName().equals("publicDetail") && point.getTarget() instanceof ArticleServiceImpl) {
                reads.add("articleDetail");
            }
            if (method.getName().equals("publicComments")) {
                reads.add("comments");
            }
            reads.forEach(store::beginRead);
            return point.proceed();
        } finally {
            PublicCacheContext.close();
        }
    }
}
