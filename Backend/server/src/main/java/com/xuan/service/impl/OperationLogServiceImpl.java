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
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.QueryTimeUtils;
import com.xuan.vo.OperationLogDetailVO;
import com.xuan.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final JsonMapper json;

    /** 分页查询后台操作日志。 */
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

    /** 根据id查询操作日志详情及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OperationLogDetailVO detail(long id) {
        requireAdmin("query");
        SysOperationLog row = required(logs.selectById(id));
        Map<String, Object> data = row.getOperationData() == null ? null
                : json.readValue(row.getOperationData(), new TypeReference<Map<String, Object>>() { });
        return new OperationLogDetailVO(row.getLogId(), row.getUserId(), row.getOperationType(),
                row.getOperationTarget(),
                row.getTargetId(), row.getPermissionCode(), row.getResult(), row.getErrorMessage(), row.getIpAddress(),
                row.getOperationTime(), data);
    }

    /** 检查本次日志操作权限，查询范围不能代替删除范围，SELF 不能当作 ALL。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "sys:log:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "当前账号没有管理全部记录的权限，请联系管理员授权。");
        }
    }

    /** 权限检查通过后才报告不存在。 */
    private SysOperationLog required(SysOperationLog row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "日志不存在");
        }
        return row;
    }

    /** 批量删除操作日志，先加锁并检查全部目标，任一失败则整批回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "sys:log", permission = "sys:log:delete")
    public void deleteBatch(List<String> ids) {
        var sorted = BatchUtils.ids(ids);
        for (long id : sorted) {
            users.lockById(sessions.currentUserId());
            requireAdmin("delete");
            SysOperationLog row = logs.lockById(id);
            requireAdmin("delete");
            required(row);
        }
        for (long id : sorted) {
            logs.deleteById(id);
        }
    }

}
