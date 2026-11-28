package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.cache.IpBlockCache;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.entity.BlogVisitor;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.UserMapper;
import com.xuan.mapper.VisitorMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IUserService;
import com.xuan.service.IVisitorService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.IpUtils;
import com.xuan.utils.QueryTimeUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.VisitorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 访问字段与限制字段分别写，防止后台保存旧统计资料覆盖并发访问。
 */
@Service
@RequiredArgsConstructor
public class VisitorServiceImpl extends ServiceImpl<VisitorMapper, BlogVisitor> implements IVisitorService {

    private final VisitorMapper visitors;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final IpBlockCache ipBlockCache;
    private final JsonMapper json;
    private final Clock clock;

    /**
     * 分页查询访客，并按当前 UTC 时刻整理已过期的封禁展示字段。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<VisitorVO> listVisitors(VisitorQueryDTO query) {
        requireAdmin("query");
        LocalDateTime at = now();
        var result = visitors.visitorPage(new Page<>(query.getPage(), query.getPageSize()), query, at);
        for (VisitorVO row : result.getRecords()) {
            normalizeRestriction(row, at);
        }
        return PageResult.from(result);
    }

    /**
     * 根据id查询访客详情及版本，已过期的封禁按正常状态返回。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<VisitorVO> detail(long id) {
        requireAdmin("query");
        VisitorVO row = visitors.visitorDetail(id);
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "访客统计不存在");
        }
        LocalDateTime at = now();
        normalizeRestriction(row, at);
        return new ResourceVersion<>(row, restrictionRevision(
                row.getIsBlocked(), row.getBlockedUntil(), row.getBlockedIpAddress(), row.getIpAddress(), at));
    }

    /**
     * 操作者和目标锁各自等待后重新检查原会话和独立ALL。
     */
    private BlogVisitor lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogVisitor row = visitors.lockById(id);
        requireAdmin(operation);
        return required(row);
    }

    /**
     * 判断封禁在指定时刻是否仍生效。
     */
    private boolean isRestrictionActive(Integer isBlocked, LocalDateTime blockedUntil, LocalDateTime at) {
        return Integer.valueOf(1).equals(isBlocked) && (blockedUntil == null || blockedUntil.isAfter(at));
    }

    /**
     * 限时封禁到期后按未封禁整理展示字段，避免列表和详情残留旧到期时间。
     */
    private void normalizeRestriction(VisitorVO row, LocalDateTime at) {
        if (!isRestrictionActive(row.getIsBlocked(), row.getBlockedUntil(), at)) {
            row.setIsBlocked(0);
            row.setBlockedUntil(null);
            row.setBlockedIpAddress(null);
        }
    }

    private String restrictionRevision(BlogVisitor row, LocalDateTime at) {
        return restrictionRevision(row.getIsBlocked(), row.getBlockedUntil(), row.getBlockedIpAddress(),
                row.getIpAddress(), at);
    }

    /**
     * 将生效中的封禁状态和目标 IP 计入版本，防止访客换网或到期后误操作。
     */
    private String restrictionRevision(Integer isBlocked, LocalDateTime blockedUntil, String blockedIp,
            String lastIp, LocalDateTime at) {
        boolean active = isRestrictionActive(isBlocked, blockedUntil, at);
        Integer effectiveBlocked = active ? 1 : 0;
        LocalDateTime effectiveUntil = active ? blockedUntil : null;
        String effectiveBlockedIp = active ? blockedIp : null;
        String targetIp = effectiveBlockedIp == null ? lastIp : effectiveBlockedIp;
        return "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(effectiveBlocked, effectiveUntil, effectiveBlockedIp, targetIp))) + "\"";
    }

    /**
     * 无独立ALL时不能查看或修改全站统计标识。
     */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:visitor:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "当前账号没有管理全部记录的权限，请联系管理员授权。");
        }
    }

    /**
     * 检查权限后才报告不存在。
     */
    private BlogVisitor required(BlogVisitor row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "访客统计不存在");
        }
        return row;
    }

    /**
     * 批量删除访客，先加锁并检查全部目标，任一失败则整批回滚。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:visitor", permission = "blog:visitor:delete")
    @CacheEvict(cacheNames = "statistics", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        LocalDateTime at = now();
        List<String> affectedIps = new ArrayList<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.getId());
            String revision = item.getRevision();
            BlogVisitor row = lock(id, "delete");
            RevisionUtils.check(restrictionRevision(row, at), revision);
            if (row.getBlockedIpAddress() != null && isRestrictionActive(row.getIsBlocked(), row.getBlockedUntil(), at)) {
                throw new BusinessException(409, "RESOURCE_IN_USE", "请先解除该记录的 IP 封禁再删除");
            }
            if (row.getBlockedIpAddress() != null) {
                affectedIps.add(row.getBlockedIpAddress());
            }
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.getId());
            visitors.deleteById(id);
        }
        ipBlockCache.evictAfterCommit(affectedIps);
    }

    /**
     * 批量封禁访客 IP，先锁定并检查全部目标，再一起保存。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-block", target = "blog:visitor", permission = "blog:visitor:block")
    public BatchVersionsVO blockBatch(List<BatchItemDTO> items, String until) {
        // 1. 按 ID 升序锁定全部目标访客，检查版本并提取规范化 IP 地址。
        var sorted = BatchUtils.sorted(items);
        List<BlogVisitor> rows = new ArrayList<>();
        List<String> affectedIps = new ArrayList<>();
        LocalDateTime end = QueryTimeUtils.parse(until);
        LocalDateTime at = now();
        for (var item : sorted) {
            long id = IdUtils.parse(item.getId());
            BlogVisitor row = lock(id, "block");
            RevisionUtils.check(restrictionRevision(row, at), item.getRevision());
            String ip = isRestrictionActive(row.getIsBlocked(), row.getBlockedUntil(), at)
                    ? row.getBlockedIpAddress()
                    : null;
            if (ip == null) {
                ip = IpUtils.normalize(row.getIpAddress());
            }
            if (ip == null) {
                throw new BusinessException(400, "VALIDATION_ERROR", "该访客没有可封禁的 IP 地址");
            }
            row.setIsBlocked(1);
            row.setBlockedUntil(end);
            row.setBlockedIpAddress(ip);
            rows.add(row);
            affectedIps.add(ip);
        }

        // 2. 校验封禁截止时间，全部通过后统一落库并在提交后精准失效 IP 封禁缓存。
        if (end != null && !end.isAfter(at)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "限制结束时间须晚于当前UTC时间");
        }
        Map<Long, String> versions = new HashMap<>();
        for (BlogVisitor row : rows) {
            visitors.restriction(row);
            versions.put(row.getVisitorId(), restrictionRevision(visitors.selectById(row.getVisitorId()), at));
        }
        ipBlockCache.evictAfterCommit(affectedIps);
        return BatchUtils.versions(items, versions);
    }

    /**
     * 批量解除访客限制，先加锁并检查全部目标，结果按原来的选择顺序返回。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-unblock", target = "blog:visitor", permission = "blog:visitor:unblock")
    public BatchVersionsVO unblockBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        List<BlogVisitor> rows = new ArrayList<>();
        LocalDateTime at = now();
        for (var item : sorted) {
            long id = IdUtils.parse(item.getId());
            BlogVisitor row = lock(id, "unblock");
            RevisionUtils.check(restrictionRevision(row, at), item.getRevision());
            rows.add(row);
        }
        Map<Long, String> versions = new HashMap<>();
        List<String> affectedIps = new ArrayList<>();
        for (BlogVisitor row : rows) {
            if (Integer.valueOf(1).equals(row.getIsBlocked())) {
                if (row.getBlockedIpAddress() != null) {
                    affectedIps.add(row.getBlockedIpAddress());
                }
                row.setIsBlocked(0);
                row.setBlockedUntil(null);
                row.setBlockedIpAddress(null);
                visitors.restriction(row);
            }
            versions.put(row.getVisitorId(), restrictionRevision(row, at));
        }
        ipBlockCache.evictAfterCommit(affectedIps);
        return BatchUtils.versions(items, versions);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

}
