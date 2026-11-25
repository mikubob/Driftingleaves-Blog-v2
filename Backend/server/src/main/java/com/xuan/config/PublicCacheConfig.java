package com.xuan.config;

import com.xuan.cache.PublicCacheSerializer;
import com.xuan.constant.CacheNames;
import com.xuan.store.PublicCacheStore;
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

/**
 * 配置公开查询缓存，统一管理各缓存域过期时间，按事务结果和缓存版本控制读写。
 */
@Configuration
@EnableCaching(order = 150)
public class PublicCacheConfig {

    private static final List<String> LONG_TTL_CACHES = List.of(
            CacheNames.HOME, CacheNames.SOCIAL, CacheNames.EXPERIENCES,
            CacheNames.SKILLS, CacheNames.LINKS, CacheNames.MUSIC, CacheNames.CONFIGS
    );
    private static final List<String> DEFAULT_TTL_CACHES = List.of(
            CacheNames.CATEGORIES, CacheNames.TAGS, CacheNames.AUTHORS,
            CacheNames.ARCHIVES, CacheNames.RSS, CacheNames.SITEMAP
    );

    /**
     * 创建缓存管理器，设置公开查询的过期时间，事务提交后再清理或写入缓存。
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory,
            PublicCacheStore store, PublicCacheSerializer serializer) {
        // 1. 构建默认缓存配置：禁用空值缓存，键用字符串，值用按返回类型反序列化的 JSON。
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(Duration.ofMinutes(30));

        // 2. 按数据更新频率为各公开缓存域分配过期时间。
        Map<String, RedisCacheConfiguration> configs = new LinkedHashMap<>();
        for (String name : LONG_TTL_CACHES) {
            configs.put(name, defaults.entryTtl(Duration.ofHours(1)));
        }
        for (String name : DEFAULT_TTL_CACHES) {
            configs.put(name, defaults);
        }
        configs.put(CacheNames.ARTICLES, defaults.entryTtl(Duration.ofMinutes(10)));
        configs.put(CacheNames.SEARCH, defaults.entryTtl(Duration.ofSeconds(60)));
        configs.put(CacheNames.ARTICLE_DETAIL, defaults.entryTtl(Duration.ofMinutes(15)));
        configs.put(CacheNames.RANKINGS, defaults.entryTtl(Duration.ofSeconds(60)));
        configs.put(CacheNames.COMMENTS, defaults.entryTtl(Duration.ofSeconds(30)));
        configs.put(CacheNames.MESSAGES, defaults.entryTtl(Duration.ofSeconds(30)));
        configs.put(CacheNames.STATISTICS, defaults.entryTtl(Duration.ofMinutes(5)));

        // 3. 每次启动切换缓存版本，避免继续读取规则变更前的旧缓存，同时保留已有写入保护标记。
        configs.keySet().forEach(store::invalidate);

        // 4. 创建事务感知的缓存管理器，所有读写和失效通过 PublicCacheStore 版本控制完成。
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(100));
        RedisCacheManager manager = new RedisCacheManager(writer, defaults, false, configs) {
            /**
             * 读写缓存时检查版本，避免其他请求保存新数据后，又把旧查询结果写回缓存。
             */
            @Override
            protected RedisCache createRedisCache(String name, RedisCacheConfiguration config) {
                return new RedisCache(name, writer, config.computePrefixWith(store::prefix)) {
                    /**
                     * 查询缓存时，同时检查版本和未提交事务的写入标记。
                     */
                    @Override
                    protected Object lookup(Object key) {
                        return store.read(name, key);
                    }

                    /**
                     * 只向本次查询开始时的缓存版本写入结果。
                     */
                    @Override
                    public void put(Object key, Object value) {
                        store.put(name, key, value, config.getTtlFunction().getTimeToLive(key, value));
                    }

                    /**
                     * 切换这类缓存的版本，让原有缓存立即失效。
                     */
                    @Override
                    public void clear() {
                        store.invalidate(name);
                    }

                    /**
                     * 主动清理时也通过切换版本让缓存失效。
                     */
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
