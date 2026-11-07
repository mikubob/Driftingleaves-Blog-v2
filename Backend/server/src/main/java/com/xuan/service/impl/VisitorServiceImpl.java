package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
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
    private final JsonMapper json;
    private final Clock clock;

    /** 分页查询访客。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<VisitorVO> listVisitors(VisitorQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(visitors.visitorPage(new Page<>(query.page(), query.pageSize()), query));
    }

    /** 根据id查询访客详情及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<VisitorVO> detail(long id) {
        requireAdmin("query");
        VisitorVO row = visitors.visitorDetail(id);
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "访客统计不存在");
        }
        return new ResourceVersion<>(row, restrictionRevision(
                row.isBlocked(), row.blockedUntil(), row.blockedIpAddress(), row.ipAddress()));
    }

    /** 操作者和目标锁各自等待后重新检查原会话和独立ALL。 */
    private BlogVisitor lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogVisitor row = visitors.lockById(id);
        requireAdmin(operation);
        return required(row);
    }

    private String restrictionRevision(BlogVisitor row) {
        return restrictionRevision(row.getIsBlocked(), row.getBlockedUntil(), row.getBlockedIpAddress(),
                row.getIpAddress());
    }

    /** 将封禁目标计入版本，防止访客换网后封错地址。 */
    private String restrictionRevision(Integer isBlocked, LocalDateTime blockedUntil, String blockedIp, String lastIp) {
        String targetIp = blockedIp == null ? lastIp : blockedIp;
        return "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(isBlocked, blockedUntil, blockedIp, targetIp))) + "\"";
    }

    /** 无独立ALL时不能查看或修改全站统计标识。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:visitor:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "当前账号没有管理全部记录的权限，请联系管理员授权。");
        }
    }

    /** 检查权限后才报告不存在。 */
    private BlogVisitor required(BlogVisitor row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "访客统计不存在");
        }
        return row;
    }

    /** 批量删除访客，先加锁并检查全部目标，任一失败则整批回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:visitor", permission = "blog:visitor:delete")
    @CacheEvict(cacheNames = "statistics", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            BlogVisitor row = lock(id, "delete");
            RevisionUtils.check(restrictionRevision(row), revision);
            LocalDateTime end = row.getBlockedUntil();
            if (row.getBlockedIpAddress() != null && (end == null || end.isAfter(now()))) {
                throw new BusinessException(409, "RESOURCE_IN_USE", "请先解除该记录的 IP 封禁再删除");
            }
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            visitors.deleteById(id);
        }
    }


    /** 批量封禁访客 IP，先锁定并检查全部目标，再一起保存。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-block", target = "blog:visitor", permission = "blog:visitor:block")
    public BatchVersionsVO blockBatch(List<BatchItemDTO> items, String until) {
        var sorted = BatchUtils.sorted(items);
        List<BlogVisitor> rows = new ArrayList<>();
        LocalDateTime end = QueryTimeUtils.parse(until);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            BlogVisitor row = lock(id, "block");
            RevisionUtils.check(restrictionRevision(row), item.revision());
            String ip = row.getBlockedIpAddress();
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
        }
        if (end != null && !end.isAfter(now())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "限制结束时间须晚于当前UTC时间");
        }
        Map<Long, String> versions = new HashMap<>();
        for (BlogVisitor row : rows) {
            visitors.restriction(row);
            versions.put(row.getVisitorId(), restrictionRevision(visitors.selectById(row.getVisitorId())));
        }
        return BatchUtils.versions(items, versions);
    }

    /** 批量解除访客限制，先加锁并检查全部目标，结果按原来的选择顺序返回。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-unblock", target = "blog:visitor", permission = "blog:visitor:unblock")
    public BatchVersionsVO unblockBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        List<BlogVisitor> rows = new ArrayList<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            BlogVisitor row = lock(id, "unblock");
            RevisionUtils.check(restrictionRevision(row), item.revision());
            rows.add(row);
        }
        Map<Long, String> versions = new HashMap<>();
        for (BlogVisitor row : rows) {
            if (row.getIsBlocked() == 1) {
                row.setIsBlocked(0);
                row.setBlockedUntil(null);
                row.setBlockedIpAddress(null);
                visitors.restriction(row);
            }
            versions.put(row.getVisitorId(), restrictionRevision(row));
        }
        return BatchUtils.versions(items, versions);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

}
