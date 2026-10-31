package com.xuan.cache;

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
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 缓存域代次与每笔事务的持久保护标记；不操作任何安全存储键。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PublicCacheStore {
    private static final Object TRANSACTION = new Object();
    private static final DefaultRedisScript<String> BEGIN = new DefaultRedisScript<>("""
            local gen = redis.call('GET', KEYS[1])
            if not gen then gen = ARGV[1]; redis.call('SET', KEYS[1], gen) end
            if redis.call('SCARD', KEYS[2]) > 0 then return '' end
            return gen
            """, String.class);
    private static final DefaultRedisScript<List> READ = new DefaultRedisScript<>("""
            local gen = redis.call('GET', KEYS[1])
            if not gen then gen = ARGV[1]; redis.call('SET', KEYS[1], gen) end
            if redis.call('SCARD', KEYS[2]) > 0 then return {'', ''} end
            return {gen, redis.call('GET', ARGV[2] .. gen .. ':' .. ARGV[3]) or ''}
            """, List.class);
    private static final DefaultRedisScript<Long> PUT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('SCARD', KEYS[2]) > 0 then return 0 end
            redis.call('SET', KEYS[3], ARGV[2], 'PX', ARGV[3]); return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> PROTECT = new DefaultRedisScript<>("""
            for _, key in ipairs(KEYS) do redis.call('SADD', key, ARGV[1]) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1])
            if ARGV[2] ~= '' then redis.call('SREM', KEYS[2], ARGV[2]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties auth;
    private final PublicCacheSerializer serializer;

    /** 当前事务只移除自己的写标记，不能解除其他事务的保护。 */
    private record Write(String token, Set<String> domains) { }

    /** 公开缓存使用独立前缀，不与会话、票据和限流键混用。 */
    public String prefix(String domain) {
        return auth.getRedisPrefix() + "cache:public:v1:" + domain + ":";
    }

    /** 在事务中的任何 SELECT 建立快照之前，记录本次读允许使用的代次。 */
    public void beginRead(String domain) {
        var frame = PublicCacheScope.current();
        String slot = "start:" + domain;
        if (frame.bypass || frame.generations.containsKey(slot)) {
            return;
        }
        String prefix = prefix(domain);
        try {
            String gen = redis.execute(BEGIN, List.of(prefix + "generation", prefix + "writers"), UUID.randomUUID().toString());
            frame.generations.put(slot, gen == null ? "" : gen);
        } catch (RuntimeException cacheUnavailable) {
            frame.generations.put(slot, "");
        }
    }

    /** GET 同时检查代次和全部写标记，未命中记住回源前代次。 */
    public Object read(String domain, Object key) {
        var frame = PublicCacheScope.current();
        if (frame == null || frame.bypass) {
            return null;
        }
        String prefix = prefix(domain);
        String query = key.toString();
        String slot = domain + ":" + query;
        frame.generations.remove(slot);
        try {
            List<?> result = redis.execute(READ, List.of(prefix + "generation", prefix + "writers"),
                    UUID.randomUUID().toString(), prefix, query);
            if (result == null || result.get(0).toString().isEmpty() ||
                    !result.get(0).toString().equals(frame.generations.get("start:" + domain))) {
                return null;
            }
            frame.generations.put(slot, result.get(0).toString());
            String data = result.get(1).toString();
            return data.isEmpty() ? null : serializer.deserialize(data.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException unavailableOrIncompatible) {
            frame.generations.remove(slot);
            return null;
        }
    }

    /** 仅能回填开始回源时的代次；事务提交前的数据由 CacheManager 延后到提交后处理。 */
    public void put(String domain, Object key, Object value, Duration ttl) {
        var frame = PublicCacheScope.current();
        if (frame == null || frame.bypass || value == null) {
            return;
        }
        String gen = frame.generations.get(domain + ":" + key);
        if (gen == null) {
            return;
        }
        String prefix = prefix(domain);
        try {
            redis.execute(PUT, List.of(prefix + "generation", prefix + "writers", prefix + gen + ":" + key),
                    gen, new String(serializer.serialize(value), StandardCharsets.UTF_8), Long.toString(ttl.toMillis()));
        } catch (RuntimeException cacheUnavailable) {
            /* 回填失败直接返回已读取的数据库结果。 */
        }
    }

    /** 首次真正执行 INSERT/UPDATE/DELETE 前登记，SELECT FOR UPDATE 不会提前登记。 */
    public void beforeMutation() {
        Set<String> domains = new LinkedHashSet<>();
        for (var frame : PublicCacheScope.CURRENT.get()) {
            domains.addAll(frame.evictions);
        }
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
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(TRANSACTION);
                    if (status == STATUS_ROLLED_BACK) {
                        // 失败时不替换原业务异常；Redis 故障留下保护供运维确认后恢复。
                        for (String domain : owned.domains()) {
                            try {
                                redis.opsForSet().remove(prefix(domain) + "writers", owned.token());
                            } catch (RuntimeException failedCleanup) {
                                log.error("公开缓存回滚保护清理失败，保留保护并按运行说明恢复");
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
        try {
            Long result = redis.execute(PROTECT, domains.stream().map(d -> prefix(d) + "writers").toList(), write.token());
            if (!Long.valueOf(1).equals(result)) {
                throw new IllegalStateException("保护登记失败");
            }
            write.domains().addAll(domains);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    /** CacheEvict 在提交后调用；轮换和移除自己的保护在同一次 Lua 中完成。 */
    public void invalidate(String domain) {
        Write write = (Write) TransactionSynchronizationManager.getResource(TRANSACTION);
        String prefix = prefix(domain);
        try {
            Long result = redis.execute(INVALIDATE, List.of(prefix + "generation", prefix + "writers"),
                    UUID.randomUUID().toString(), write == null ? "" : write.token());
            if (!Long.valueOf(1).equals(result)) {
                throw new IllegalStateException("失效失败");
            }
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    /** 写保护登记或失效失败时阻止业务继续，对外不暴露 Redis 细节。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "公开缓存失效保护暂不可用");
    }
}
