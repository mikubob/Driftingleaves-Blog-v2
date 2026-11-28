package com.xuan.cache;

import com.xuan.constant.CacheNames;
import com.xuan.mapper.AuthorizationMapper;
import com.xuan.vo.CapabilitiesVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 缓存用户有效角色、权限码及数据范围，授权变更提交后按用户 ID 精准失效。
 */
@Component
public class UserAuthzCache {

    private final AuthorizationMapper mapper;
    private final CacheManager simpleCacheManager;
    private final TransactionTemplate readOnlyTx;

    public UserAuthzCache(AuthorizationMapper mapper,
            @Qualifier(CacheNames.SIMPLE_CACHE_MANAGER) CacheManager simpleCacheManager,
            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.simpleCacheManager = simpleCacheManager;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.readOnlyTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * 读取用户当前有效角色、权限码和各自数据范围；命中缓存时不开启数据库事务，未命中时在同一只读事务中回源读取。
     */
    @Cacheable(
            cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
            cacheNames = CacheNames.USER_AUTHZ,
            key = "#userId",
            unless = "#result == null"
    )
    public CapabilitiesVO loadCapabilities(long userId) {
        return readOnlyTx.execute(status -> {
            // 1. 按权限码合并真正授予它的角色范围，用 TreeMap 让响应按字典序排列。
            Map<String, String> scopes = new TreeMap<>();
            for (AuthorizationMapper.Grant grant : mapper.grants(userId)) {
                scopes.merge(grant.getPermissionCode(), grant.getDataScope(),
                        (left, right) -> "ALL".equals(left) || "ALL".equals(right) ? "ALL" : "SELF");
            }

            // 2. 返回有效角色、权限和各自范围，不能按角色名给通配权限。
            return CapabilitiesVO.builder()
                    .roleCodes(mapper.roleCodes(userId))
                    .permissionCodes(List.copyOf(scopes.keySet()))
                    .dataScopes(scopes)
                    .build();
        });
    }

    /**
     * 在当前事务提交后失效指定用户的授权缓存；无事务时立即失效。
     */
    public void evictAfterCommit(long userId) {
        evictAfterCommit(List.of(userId));
    }

    /**
     * 在当前事务提交后批量失效受影响用户的授权缓存；无事务时立即失效。
     */
    public void evictAfterCommit(Collection<Long> userIds) {
        Set<Long> targets = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId != null) {
                    targets.add(userId);
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictUsers(targets);
                }
            });
            return;
        }
        evictUsers(targets);
    }

    private void evictUsers(Set<Long> userIds) {
        Cache cache = simpleCacheManager.getCache(CacheNames.USER_AUTHZ);
        if (cache == null) {
            return;
        }
        for (Long userId : userIds) {
            cache.evict(userId);
        }
    }
}
