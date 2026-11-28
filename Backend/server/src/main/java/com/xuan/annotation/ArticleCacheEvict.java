package com.xuan.annotation;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 文章变更后统一清理受影响的 12 个公开缓存域，由 Spring Cache 与 PublicCacheAspect 共同解析。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Caching(evict = {
        @CacheEvict(cacheNames = "articles", allEntries = true),
        @CacheEvict(cacheNames = "search", allEntries = true),
        @CacheEvict(cacheNames = "articleDetail", allEntries = true),
        @CacheEvict(cacheNames = "archives", allEntries = true),
        @CacheEvict(cacheNames = "rankings", allEntries = true),
        @CacheEvict(cacheNames = "categories", allEntries = true),
        @CacheEvict(cacheNames = "tags", allEntries = true),
        @CacheEvict(cacheNames = "authors", allEntries = true),
        @CacheEvict(cacheNames = "statistics", allEntries = true),
        @CacheEvict(cacheNames = "rss", allEntries = true),
        @CacheEvict(cacheNames = "sitemap", allEntries = true),
        @CacheEvict(cacheNames = "comments", allEntries = true)
})
public @interface ArticleCacheEvict {
}
