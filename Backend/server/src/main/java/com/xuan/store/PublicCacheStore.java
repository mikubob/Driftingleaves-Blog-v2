package com.xuan.store;

import com.xuan.cache.PublicCacheContext;
import com.xuan.cache.PublicCacheSerializer;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 保存各类公开缓存的版本和事务写入标记，不操作登录、票据等安全相关的 Redis 键。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PublicCacheStore {
    private static final Object TRANSACTION = new Object();
    // 使用独立的 ZSET 键名保存带超时时间的写保护令牌，避免与旧版 SET 键类型冲突。
    private static final String WRITERS_SUFFIX = "writers:v2";
    private static final long WRITER_TTL_MS = Duration.ofMinutes(2).toMillis();

    private static final DefaultRedisScript<String> BEGIN = new DefaultRedisScript<>("""
            local gen = redis.call('GET', KEYS[1])
            if not gen then gen = ARGV[1]; redis.call('SET', KEYS[1], gen) end
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2])
            if redis.call('ZCARD', KEYS[2]) > 0 then return '' end
            return gen
            """, String.class);
    private static final DefaultRedisScript<List> READ = new DefaultRedisScript<>("""
            local gen = redis.call('GET', KEYS[1])
            if not gen then gen = ARGV[1]; redis.call('SET', KEYS[1], gen) end
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[4])
            if redis.call('ZCARD', KEYS[2]) > 0 then return {'', ''} end
            return {gen, redis.call('GET', ARGV[2] .. gen .. ':' .. ARGV[3]) or ''}
            """, List.class);
    private static final DefaultRedisScript<Long> PUT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[4])
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('ZCARD', KEYS[2]) > 0 then return 0 end
            redis.call('SET', KEYS[3], ARGV[2], 'PX', ARGV[3]); return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> PROTECT = new DefaultRedisScript<>("""
            for _, key in ipairs(KEYS) do
                redis.call('ZREMRANGEBYSCORE', key, '-inf', ARGV[2])
                redis.call('ZADD', key, ARGV[3], ARGV[1])
                redis.call('PEXPIRE', key, ARGV[4])
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1])
            if ARGV[2] ~= '' then redis.call('ZREM', KEYS[2], ARGV[2]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties auth;
    private final PublicCacheSerializer serializer;
    private final Clock clock;

    /**
     * 当前事务只移除自己的写标记，不能解除其他事务的保护。
     */
    private record Write(String token, Set<String> domains) { }

    /**
     * 公开缓存使用独立前缀，不与会话、票据和限流键混用。
     */
    public String prefix(String domain) {
        return auth.getRedisPrefix() + "cache:public:v1:" + domain + ":";
    }

    /**
     * 在事务第一次执行 SELECT 前，记下本次查询可以使用的缓存版本。
     */
    public void beginRead(String domain) {
        var frame = PublicCacheContext.current();
        String slot = "start:" + domain;
        if (frame.bypass() || frame.generations().containsKey(slot)) {
            return;
        }
        String prefix = prefix(domain);
        try {
            String gen = redis.execute(BEGIN, List.of(prefix + "generation", writersKey(domain)),
                    UUID.randomUUID().toString(), nowMillis());
            frame.generations().put(slot, gen == null ? "" : gen);
        } catch (RuntimeException cacheUnavailable) {
            frame.generations().put(slot, "");
        }
    }

    /**
     * 读取缓存时同时检查版本和未过期写入标记；缓存未命中时，记下查数据库前的缓存版本。
     */
    public Object read(String domain, Object key) {
        var frame = PublicCacheContext.current();
        if (frame == null || frame.bypass()) {
            return null;
        }
        String prefix = prefix(domain);
        String query = key.toString();
        String slot = domain + ":" + query;
        frame.generations().remove(slot);
        try {
            List<?> result = redis.execute(READ, List.of(prefix + "generation", writersKey(domain)),
                    UUID.randomUUID().toString(), prefix, query, nowMillis());
            if (result == null || result.get(0).toString().isEmpty() ||
                    !result.get(0).toString().equals(frame.generations().get("start:" + domain))) {
                return null;
            }
            frame.generations().put(slot, result.get(0).toString());
            String data = result.get(1).toString();
            return data.isEmpty() ? null : serializer.deserialize(data.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException unavailableOrIncompatible) {
            frame.generations().remove(slot);
            return null;
        }
    }

    /**
     * 保存查询结果时，只能写入查数据库前记下的缓存版本；事务提交前的数据由 CacheManager 等到提交后再处理。
     */
    public void put(String domain, Object key, Object value, Duration ttl) {
        var frame = PublicCacheContext.current();
        if (frame == null || frame.bypass() || value == null) {
            return;
        }
        String gen = frame.generations().get(domain + ":" + key);
        if (gen == null) {
            return;
        }
        String prefix = prefix(domain);
        try {
            redis.execute(PUT, List.of(prefix + "generation", writersKey(domain), prefix + gen + ":" + key),
                    gen, new String(serializer.serialize(value), StandardCharsets.UTF_8),
                    Long.toString(ttl.toMillis()), nowMillis());
        } catch (RuntimeException cacheUnavailable) {
            /* 回填失败直接返回已读取的数据库结果。 */
        }
    }

    /**
     * 首次真正执行 INSERT/UPDATE/DELETE 前登记带超时时间的写保护令牌，SELECT FOR UPDATE 不会提前登记。
     */
    public void beforeMutation() {
        Set<String> domains = PublicCacheContext.evictions();
        if (domains.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("公开缓存写保护必须在事务中登记");
        }
        Write write = (Write) TransactionSynchronizationManager.getResource(TRANSACTION);
        if (write == null) {
            write = new Write(UUID.randomUUID().toString(), new LinkedHashSet<>());
            TransactionSynchronizationManager.bindResource(TRANSACTION, write);
            Write owned = write;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 事务结束后解绑写入记录；回滚时移除令牌，状态未知时先轮换版本再移除令牌防止旧缓存复活。
                 */
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(TRANSACTION);
                    if (status == STATUS_ROLLED_BACK) {
                        // 失败时不替换原业务异常；清理失败的令牌也会在超时后自动过期。
                        for (String domain : owned.domains()) {
                            try {
                                redis.opsForZSet().remove(writersKey(domain), owned.token());
                            } catch (RuntimeException failedCleanup) {
                                log.error("公开缓存回滚保护清理失败，令牌将在超时后自动失效: domain={}", domain, failedCleanup);
                            }
                        }
                        return;
                    }
                    if (status == STATUS_UNKNOWN) {
                        // 提交状态未知时可能已落库，必须先轮换版本再移除保护令牌，不能直接放行旧缓存。
                        for (String domain : owned.domains()) {
                            try {
                                rotateAndRemove(domain, owned.token());
                            } catch (RuntimeException failedCleanup) {
                                log.error("公开缓存未知事务状态轮换失败，令牌将在超时后自动失效: domain={}", domain, failedCleanup);
                            }
                        }
                    }
                }
            });
        }
        domains.removeAll(write.domains());
        if (domains.isEmpty()) {
            return;
        }
        long now = clock.millis();
        long expireAt = now + WRITER_TTL_MS;
        try {
            Long result = redis.execute(PROTECT, domains.stream().map(this::writersKey).toList(),
                    write.token(), Long.toString(now), Long.toString(expireAt), Long.toString(WRITER_TTL_MS));
            if (!Long.valueOf(1).equals(result)) {
                throw new IllegalStateException("保护登记失败");
            }
            write.domains().addAll(domains);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    /**
     * CacheEvict 在提交后调用；轮换和移除自己的保护在同一次 Lua 中完成。
     */
    public void invalidate(String domain) {
        Write write = (Write) TransactionSynchronizationManager.getResource(TRANSACTION);
        try {
            rotateAndRemove(domain, write == null ? "" : write.token());
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    /**
     * 轮换缓存版本并移除指定写保护令牌。
     */
    private void rotateAndRemove(String domain, String token) {
        String prefix = prefix(domain);
        Long result = redis.execute(INVALIDATE, List.of(prefix + "generation", writersKey(domain)),
                UUID.randomUUID().toString(), token == null ? "" : token);
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("失效失败");
        }
    }

    /**
     * 返回指定缓存域的写保护 ZSET 键名。
     */
    private String writersKey(String domain) {
        return prefix(domain) + WRITERS_SUFFIX;
    }

    /**
     * 返回当前 UTC 毫秒时间戳字符串。
     */
    private String nowMillis() {
        return Long.toString(clock.millis());
    }

    /**
     * 写保护登记或失效失败时阻止业务继续，对外不暴露 Redis 细节。
     */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "公开缓存失效保护暂不可用");
    }
}
