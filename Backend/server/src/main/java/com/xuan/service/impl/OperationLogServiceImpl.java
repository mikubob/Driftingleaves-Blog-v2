package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.entity.SysOperationLog;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.OperationLogMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IOperationLogService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.utils.QueryTimeUtils;
import com.xuan.vo.OperationLogDetailVO;
import com.xuan.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 查询与清理分别使用独立权限，清理自身也要留一条新日志。
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, SysOperationLog> implements IOperationLogService {

    private final OperationLogMapper logs;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<OperationLogVO> listLogs(OperationLogQueryDTO query) {
        requireAdmin("query");
        LocalDateTime begin = QueryTimeUtils.parse(query.begin());
        LocalDateTime end = QueryTimeUtils.parse(query.end());
        QueryTimeUtils.requireRange(begin, end);
        return PageResult.from(logs.logPage(new Page<>(query.page(), query.pageSize()), query,
                IdUtils.parse(query.userId()), IdUtils.parse(query.targetId()), begin, end));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OperationLogDetailVO detail(long id) {
        requireAdmin("query");
        SysOperationLog row = required(logs.selectById(id));
        // 现有审计写入者没有摘要白名单，不把数据库里的任意JSON直接当公开字段。
        return new OperationLogDetailVO(row.getLogId(), row.getUserId(), row.getOperationType(),
                row.getOperationTarget(),
                row.getTargetId(), row.getPermissionCode(), row.getResult(), row.getErrorMessage(), row.getIpAddress(),
                row.getOperationTime(), null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "sys:log", permission = "sys:log:delete", targetIdArgument = 0)
    public void delete(long id) {
        users.lockById(sessions.currentUserId());
        requireAdmin("delete");
        SysOperationLog row = logs.lockById(id);
        requireAdmin("delete");
        required(row);
        logs.deleteById(id);
    }

    /** 不借query范围放宽delete，也不把SELF伪装为全局权限。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "sys:log:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 权限检查通过后才报告不存在。 */
    private SysOperationLog required(SysOperationLog row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "日志不存在");
        }
        return row;
    }
}
