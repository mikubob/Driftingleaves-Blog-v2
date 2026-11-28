package com.xuan.cache;

import com.xuan.constant.CacheNames;
import com.xuan.mapper.VisitorMapper;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 缓存访客 IP 手动封禁状态，读取回填带版本校验，后台封禁、解封或删除访客提交后按 IP 摘要精准失效。
 */
@Component
@RequiredArgsConstructor
public class IpBlockCache {

    public static final Duration TTL = Duration.ofSeconds(60);

    private final VisitorMapper visitors;
    private final VersionedAccessCache accessCache;
    private final Clock clock;

    /**
     * 按 IP 摘要读取封禁状态，命中后仍由调用方按到期时间戳判断限时封禁是否已自然解除。
     */
    public IpBlockState getState(String ipDigest, String ip) {
        return accessCache.getOrLoad(CacheNames.IP_BLOCK, ipDigest, TTL, IpBlockState.class, () -> loadState(ip));
    }

    /**
     * 在当前事务提交后按 IP 摘要推进版本并精准失效封禁缓存；事务状态未知时也执行失效，无事务时立即失效。
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
                    accessCache.invalidate(CacheNames.IP_BLOCK, digests, TTL);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_UNKNOWN) {
                        accessCache.invalidate(CacheNames.IP_BLOCK, digests, TTL);
                    }
                }
            });
            return;
        }
        accessCache.invalidate(CacheNames.IP_BLOCK, digests, TTL);
    }

    /**
     * 直查数据库汇总指定 IP 当前有效的手动封禁状态。
     */
    private IpBlockState loadState(String ip) {
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
