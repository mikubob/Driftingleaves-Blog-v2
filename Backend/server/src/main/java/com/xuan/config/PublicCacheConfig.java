package com.xuan.config;

import com.xuan.cache.PublicCacheSerializer;
import com.xuan.cache.PublicCacheStore;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 公开缓存使用声明式注解，固定 TTL、独立前缀、事务感知与业务代次。 */
@Configuration
@EnableCaching(order = 150)
public class PublicCacheConfig {
    /** 固定各公开查询的有效期，提交后失效与回填仍由事务感知管理器处理。 */
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
            /** 缓存读写交给带代次校验的存储，避免并发写入后回填旧值。 */
            @Override
            protected RedisCache createRedisCache(String name, RedisCacheConfiguration config) {
                return new RedisCache(name, writer, config.computePrefixWith(n -> store.prefix(n))) {
                    /** 查询时同时核对代次与尚未提交的写标记。 */
                    @Override
                    protected Object lookup(Object key) {
                        return store.read(name, key);
                    }

                    /** 只允许回填本次查询开始时的代次。 */
                    @Override
                    public void put(Object key, Object value) {
                        store.put(name, key, value, config.getTtlFunction().getTimeToLive(key, value));
                    }

                    /** 轮换代次，使该域已有条目立即失效。 */
                    @Override
                    public void clear() {
                        store.invalidate(name);
                    }

                    /** 主动清理也走相同的代次失效流程。 */
                    @Override
                    public boolean invalidate() {
                        clear();
                        return true;
                    }
                };
            }
        };
        manager.setTransactionAware(true);
        return manager;
    }
}
