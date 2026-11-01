package com.xuan.cache;

import cn.hutool.core.collection.CollUtil;
import com.xuan.service.impl.ArticleServiceImpl;
import com.xuan.service.impl.SiteConfigServiceImpl;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在事务建立读取快照之前开始缓存上下文，内部代理调用共用查询代次。 */
@Aspect
@Component
@Order(80)
@RequiredArgsConstructor
public class PublicCacheScope {
    static final ThreadLocal<Deque<Frame>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

    private final PublicCacheStore store;

    /** 每层调用保存返回类型和失效范围，嵌套调用共用查询代次。 */
    static class Frame {
        final Type type;
        final Set<String> evictions = new LinkedHashSet<>();
        final Map<String, String> generations;
        final boolean bypass;

        Frame(Type type, Frame parent) {
            this.type = type;
            generations = parent == null ? new HashMap<>() : parent.generations;
            bypass = parent == null ? TransactionSynchronizationManager.isActualTransactionActive() : parent.bypass;
        }
    }

    /** 序列化与存储只读取当前调用的类型、代次和事务状态。 */
    static Frame current() {
        return CURRENT.get().peek();
    }

    /** 只处理声明了缓存的业务方法，不将私人服务或通用 Mapper 放进缓存。 */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable) || " +
            "@annotation(org.springframework.cache.annotation.CacheEvict) || @annotation(org.springframework.cache.annotation.Caching) || " +
            "execution(* com.xuan.service.impl.ArticleServiceImpl.publicArticles(..)) || " +
            "execution(* com.xuan.service.impl.ArticleServiceImpl.publicDetail(..)) || " +
            "execution(* com.xuan.service.impl.CommentServiceImpl.publicComments(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        var method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        Frame frame = new Frame(method.getGenericReturnType(), current());
        CacheEvict single = method.getAnnotation(CacheEvict.class);
        Caching multiple = method.getAnnotation(Caching.class);
        if (single != null) {
            frame.evictions.addAll(Arrays.asList(single.cacheNames()));
        }
        if (multiple != null) {
            for (CacheEvict evict : multiple.evict()) {
                frame.evictions.addAll(Arrays.asList(evict.cacheNames()));
            }
        }
        // 只有 site 的公开资料依赖缓存，审核开关仍实时读写。
        if (point.getTarget() instanceof SiteConfigServiceImpl &&
                point.getArgs().length > 0 && !"site".equals(point.getArgs()[0])) {
            frame.evictions.clear();
        }
        CURRENT.get().push(frame);
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
            CURRENT.get().pop();
            if (CollUtil.isEmpty(CURRENT.get())) {
                CURRENT.remove();
            }
        }
    }
}
