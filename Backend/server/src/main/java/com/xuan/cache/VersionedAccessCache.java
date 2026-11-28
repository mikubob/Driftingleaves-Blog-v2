package com.xuan.cache;

import com.xuan.properties.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 为访问控制类单键缓存提供版本号校验与失效失败兜底，防止并发读回写旧状态或在失效异常时继续命中旧缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionedAccessCache {

    private static final long MIN_VERSION_TTL_MS = Duration.ofHours(24).toMillis();
    private static final int BYPASS_CLEANUP_THRESHOLD = 1024;

    private static final DefaultRedisScript<List> READ = new DefaultRedisScript<>("""
            local ver = redis.call('GET', KEYS[1]) or '0'
            local val = redis.call('GET', KEYS[2]) or ''
            return {ver, val}
            """, List.class);

    private static final DefaultRedisScript<Long> PUT_IF_VERSION = new DefaultRedisScript<>("""
            local cur = redis.call('GET', KEYS[1]) or '0'
            if cur ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            local ver = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            redis.call('DEL', KEYS[2])
            return ver
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties auth;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicLong bypassEpoch = new AtomicLong();
    private final ConcurrentHashMap<String, BypassWindow> bypassUntil = new ConcurrentHashMap<>();

    /**
     * 记录单次失效失败生成的本地绕过窗口，带单调递增序号以防并发失效误删更新的绕过标记。
     */
    private record BypassWindow(long epoch, long expireAt) {
    }

    /**
     * 读取带版本号的缓存；未命中时回源查询，仅在版本未变时回填，避免并发写后旧值重新进入缓存。
     *
     * @param domain 缓存域名称
     * @param key 业务键
     * @param ttl 缓存过期时间
     * @param type 返回值类型
     * @param loader 缓存未命中或故障时的回源查询
     * @return 缓存或数据库中的最新状态
     */
    public <T> T getOrLoad(String domain, String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        String slot = slot(domain, key);

        // 1. 若该键此前失效失败仍在兜底窗口内，尝试补发失效并直接回源查库，不读取也不回填旧缓存。
        if (shouldBypass(slot, domain, key, ttl)) {
            return loader.get();
        }

        // 2. 原子读取当前版本号与缓存值；Redis 异常时降级直查数据库且跳过回填。
        String versionKey = versionKey(domain, key);
        String valueKey = valueKey(domain, key);
        String versionBeforeLoad;
        try {
            List<?> result = redis.execute(READ, List.of(versionKey, valueKey));
            if (result == null || result.size() < 2) {
                return loader.get();
            }
            versionBeforeLoad = String.valueOf(result.get(0));
            String raw = result.get(1) == null ? "" : result.get(1).toString();
            if (!raw.isEmpty()) {
                return json.readValue(raw, type);
            }
        } catch (RuntimeException exception) {
            log.warn("访问控制缓存读取失败，已降级直查数据库: domain={}, key={}", domain, key, exception);
            return loader.get();
        }

        // 3. 缓存未命中时回源查询，回填前复核兜底状态与 Redis 版本号，版本已推进则放弃回填。
        T loaded = loader.get();
        if (loaded == null || isBypassActive(slot)) {
            return loaded;
        }
        try {
            String serialized = json.writeValueAsString(loaded);
            redis.execute(PUT_IF_VERSION, List.of(versionKey, valueKey),
                    versionBeforeLoad, serialized, Long.toString(ttl.toMillis()));
        } catch (RuntimeException exception) {
            log.warn("访问控制缓存回填失败，已返回数据库结果: domain={}, key={}", domain, key, exception);
        }
        return loaded;
    }

    /**
     * 批量推进版本号并删除旧缓存；失败时重试一次，仍失败则在本地标记绕过窗口直到旧缓存必然过期。
     *
     * @param domain 缓存域名称
     * @param keys 受影响的业务键集合
     * @param ttl 该缓存域的数据过期时间
     */
    public void invalidate(String domain, Collection<String> keys, Duration ttl) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            invalidateSingle(domain, key, ttl);
        }
    }

    /**
     * 对单个键执行版本推进与删除，首次失败时立即重试一次；成功时仅清除本次调用前已确认的绕过标记，避免覆盖并发的新失效失败标记。
     */
    private void invalidateSingle(String domain, String key, Duration ttl) {
        String slot = slot(domain, key);
        BypassWindow beforeFirst = bypassUntil.get(slot);
        if (tryInvalidate(domain, key, ttl)) {
            clearObservedBypass(slot, beforeFirst);
            return;
        }
        BypassWindow beforeRetry = bypassUntil.get(slot);
        if (tryInvalidate(domain, key, ttl)) {
            clearObservedBypass(slot, beforeRetry);
            return;
        }
        markBypass(slot, ttl);
        log.error("访问控制缓存失效重试仍失败，已在本地启用绕过窗口直至旧缓存过期: domain={}, key={}", domain, key);
    }

    /**
     * 检查当前键是否处于失效失败兜底窗口；在窗口内顺手尝试补发失效，成功后仅清除本次确认过的绕过标记。
     */
    private boolean shouldBypass(String slot, String domain, String key, Duration ttl) {
        BypassWindow window = bypassUntil.get(slot);
        if (window == null) {
            return false;
        }
        long now = clock.millis();
        if (now >= window.expireAt()) {
            bypassUntil.remove(slot, window);
            return false;
        }
        if (tryInvalidate(domain, key, ttl)) {
            bypassUntil.remove(slot, window);
        }
        return true;
    }

    /**
     * 判断指定键当前是否仍在本地绕过窗口内。
     */
    private boolean isBypassActive(String slot) {
        BypassWindow window = bypassUntil.get(slot);
        if (window == null) {
            return false;
        }
        if (clock.millis() >= window.expireAt()) {
            bypassUntil.remove(slot, window);
            return false;
        }
        return true;
    }

    /**
     * 仅清除本次失效开始前已观测到的绕过窗口，绝不误删并发期间新登记的绕过标记。
     */
    private void clearObservedBypass(String slot, BypassWindow observed) {
        if (observed != null) {
            bypassUntil.remove(slot, observed);
        }
    }

    /**
     * 登记带全新序号的本地绕过窗口并按需清理已过期条目，防止异常期间集合持续增长。
     */
    private void markBypass(String slot, Duration ttl) {
        long now = clock.millis();
        long deadline = now + ttl.toMillis();
        bypassUntil.compute(slot, (k, existing) -> {
            long expireAt = existing == null ? deadline : Math.max(existing.expireAt(), deadline);
            return new BypassWindow(bypassEpoch.incrementAndGet(), expireAt);
        });
        if (bypassUntil.size() > BYPASS_CLEANUP_THRESHOLD) {
            for (Map.Entry<String, BypassWindow> entry : bypassUntil.entrySet()) {
                if (now >= entry.getValue().expireAt()) {
                    bypassUntil.remove(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * 执行一次原子版本推进并删除旧缓存值，成功返回 true。
     */
    private boolean tryInvalidate(String domain, String key, Duration ttl) {
        try {
            long versionTtlMs = Math.max(ttl.toMillis() * 4L, MIN_VERSION_TTL_MS);
            Long result = redis.execute(INVALIDATE, List.of(versionKey(domain, key), valueKey(domain, key)),
                    Long.toString(versionTtlMs));
            return result != null && result > 0L;
        } catch (RuntimeException exception) {
            log.warn("访问控制缓存失效执行失败: domain={}, key={}", domain, key, exception);
            return false;
        }
    }

    /**
     * 构造本地绕过表的组合键。
     */
    private String slot(String domain, String key) {
        return domain + ":" + key;
    }

    /**
     * 构造指定域与业务键的 Redis 版本号键名。
     */
    private String versionKey(String domain, String key) {
        return auth.getRedisPrefix() + "cache:access:v1:" + domain + ":ver:" + key;
    }

    /**
     * 构造指定域与业务键的 Redis 缓存值键名。
     */
    private String valueKey(String domain, String key) {
        return auth.getRedisPrefix() + "cache:access:v1:" + domain + ":val:" + key;
    }
}
