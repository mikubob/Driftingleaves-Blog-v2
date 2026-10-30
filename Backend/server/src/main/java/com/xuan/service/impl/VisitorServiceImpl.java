package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
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
import com.xuan.utils.QueryTimeUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogVisitor> listVisitors(VisitorQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(visitors.visitorPage(new Page<>(query.page(), query.pageSize()), query));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<BlogVisitor> detail(long id) {
        requireAdmin("query");
        return profile(required(visitors.selectById(id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "block", target = "blog:visitor", permission = "blog:visitor:block", targetIdArgument = 0)
    public ResourceVersion<BlogVisitor> block(long id, String until, String revision) {
        BlogVisitor row = lock(id, "block");
        RevisionUtils.check(profile(row).revision(), revision);
        LocalDateTime end = QueryTimeUtils.parse(until);
        if (end != null && !end.isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw new BusinessException(400, "VALIDATION_ERROR", "限制结束时间须晚于当前UTC时间");
        }
        row.setIsBlocked(1);
        row.setBlockedUntil(end);
        visitors.restriction(row);
        return profile(visitors.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "unblock", target = "blog:visitor", permission = "blog:visitor:unblock", targetIdArgument = 0)
    public ResourceVersion<BlogVisitor> unblock(long id, String revision) {
        BlogVisitor row = lock(id, "unblock");
        RevisionUtils.check(profile(row).revision(), revision);
        if (row.getIsBlocked() != 0 || row.getBlockedUntil() != null) {
            row.setIsBlocked(0);
            row.setBlockedUntil(null);
            visitors.restriction(row);
        }
        return profile(visitors.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:visitor", permission = "blog:visitor:delete", targetIdArgument = 0)
    public void delete(long id, String revision) {
        BlogVisitor row = lock(id, "delete");
        RevisionUtils.check(profile(row).revision(), revision);
        visitors.deleteById(id);
    }

    /** 操作者和目标锁各自等待后重新检查原会话和独立ALL。 */
    private BlogVisitor lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogVisitor row = visitors.lockById(id);
        requireAdmin(operation);
        return required(row);
    }

    /** 限制字段是完整业务版本，不受持续变化的IP/UA和访问时间干扰。 */
    private ResourceVersion<BlogVisitor> profile(BlogVisitor row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(
                json.writeValueAsString(Arrays.asList(row.getIsBlocked(), row.getBlockedUntil()))) + "\"");
    }

    /** 无独立ALL时不能查看或修改全站统计标识。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:visitor:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 检查权限后才报告不存在。 */
    private BlogVisitor required(BlogVisitor row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "访客统计不存在");
        }
        return row;
    }
}
