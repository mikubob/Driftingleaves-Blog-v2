package com.xuan.config;

import com.xuan.cache.PublicCacheStore;
import com.xuan.cache.PublicCacheSerializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.time.Duration;
import java.util.*;

/** 公开缓存使用声明式注解，固定 TTL、独立前缀、事务感知与业务代次。 */
@Configuration
@EnableCaching(order = 150)
public class PublicCacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, PublicCacheStore store, PublicCacheSerializer serializer) {
        var defaults = RedisCacheConfiguration.defaultCacheConfig().disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        serializer))
                .entryTtl(Duration.ofMinutes(30));
        Map<String, RedisCacheConfiguration> configs = new LinkedHashMap<>();
        for (String name : List.of("home", "social", "experiences", "skills", "links", "music", "configs")) {
            configs.put(name, defaults.entryTtl(Duration.ofHours(1)));
        }
        for (String name : List.of("categories", "tags", "authors", "archives", "rss", "sitemap")) {
            configs.put(name, defaults);
        }
        configs.put("articles", defaults.entryTtl(Duration.ofMinutes(10)));
        configs.put("search", defaults.entryTtl(Duration.ofSeconds(60)));
        configs.put("articleDetail", defaults.entryTtl(Duration.ofMinutes(15)));
        configs.put("rankings", defaults.entryTtl(Duration.ofSeconds(60)));
        configs.put("comments", defaults.entryTtl(Duration.ofSeconds(30)));
        configs.put("statistics", defaults.entryTtl(Duration.ofMinutes(5)));
        // 每次启动切换共享代次，部署基址/渲染变化及恢复的旧条目不能继续命中；保留遗留写保护。
        configs.keySet().forEach(store::invalidate);
        var writer = RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(100));
        RedisCacheManager manager = new RedisCacheManager(writer, defaults, false, configs) {
            @Override protected RedisCache createRedisCache(String name, RedisCacheConfiguration config) {
                return new RedisCache(name, writer, config.computePrefixWith(n -> store.prefix(n))) {
                    @Override protected Object lookup(Object key) { return store.read(name, key); }
                    @Override public void put(Object key, Object value) { store.put(name, key, value, config.getTtlFunction().getTimeToLive(key, value)); }
                    @Override public void clear() { store.invalidate(name); }
                    @Override public boolean invalidate() { clear(); return true; }
                };
            }
        };
        manager.setTransactionAware(true);
        return manager;
    }
}
