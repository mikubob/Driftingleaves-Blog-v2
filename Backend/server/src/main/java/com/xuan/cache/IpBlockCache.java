package com.xuan.cache;

import com.xuan.constant.CacheNames;
import com.xuan.mapper.VisitorMapper;
import com.xuan.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 缓存访客 IP 手动封禁状态，后台封禁、解封或删除访客提交后按 IP 摘要精准失效。
 */
@Component
public class IpBlockCache {

    private final VisitorMapper visitors;
    private final CacheManager simpleCacheManager;
    private final Clock clock;

    public IpBlockCache(VisitorMapper visitors,
            @Qualifier(CacheNames.SIMPLE_CACHE_MANAGER) CacheManager simpleCacheManager,
            Clock clock) {
        this.visitors = visitors;
        this.simpleCacheManager = simpleCacheManager;
        this.clock = clock;
    }

    /**
     * 按 IP 摘要读取封禁状态，命中后仍由调用方按到期时间戳判断限时封禁是否已自然解除。
     */
    @Cacheable(
            cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
            cacheNames = CacheNames.IP_BLOCK,
            key = "#ipDigest",
            unless = "#result == null"
    )
    public IpBlockState getState(String ipDigest, String ip) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        VisitorMapper.IpBlockSummary summary = visitors.activeIpBlockSummary(ip, now);
        if (summary == null || summary.getActiveCount() <= 0) {
            return new IpBlockState(false, null);
        }
        if (summary.getPermanentCount() > 0 || summary.getLatestUntil() == null) {
            return new IpBlockState(true, null);
        }
        long untilMillis = summary.getLatestUntil().toInstant(ZoneOffset.UTC).toEpochMilli();
        return new IpBlockState(true, untilMillis);
    }

    /**
     * 在当前事务提交后按 IP 摘要精准失效封禁缓存；无事务时立即失效。
     */
    public void evictAfterCommit(Collection<String> ips) {
        Set<String> digests = new LinkedHashSet<>();
        if (ips != null) {
            for (String ip : ips) {
                if (ip != null && !ip.isBlank()) {
                    digests.add(TokenUtils.digest(ip));
                }
            }
        }
        if (digests.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictDigests(digests);
                }
            });
            return;
        }
        evictDigests(digests);
    }

    private void evictDigests(Set<String> digests) {
        Cache cache = simpleCacheManager.getCache(CacheNames.IP_BLOCK);
        if (cache == null) {
            return;
        }
        for (String digest : digests) {
            cache.evict(digest);
        }
    }

    /**
     * 缓存的 IP 封禁状态，blockedUntilEpochMilli 为 null 表示未封禁或永久封禁。
     */
    public record IpBlockState(boolean blocked, Long blockedUntilEpochMilli) {

        /**
         * 判断该封禁状态在指定 UTC 毫秒时刻是否仍然生效。
         */
        public boolean isActiveAt(long nowMillis) {
            return blocked && (blockedUntilEpochMilli == null || blockedUntilEpochMilli > nowMillis);
        }
    }
}
