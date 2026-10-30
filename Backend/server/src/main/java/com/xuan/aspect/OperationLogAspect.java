package com.xuan.aspect;

import com.xuan.annotation.OperationLog;
import com.xuan.audit.FailureAuditWriter;
import com.xuan.entity.SysOperationLog;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.OperationLogMapper;
import com.xuan.properties.AuthProperties;
import com.xuan.session.SessionManager;
import com.xuan.utils.IpUtils;
import com.xuan.vo.MenuVO;
import com.xuan.vo.PermissionVO;
import com.xuan.vo.RoleVO;
import com.xuan.vo.UserAdminVO;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/** 写事务内部协调审计；成功日志写失败使业务回滚，失败日志在回滚完成后另写。 */
@Aspect
@Component
@Order(200)
@RequiredArgsConstructor
public class OperationLogAspect {
    private static final Set<String> SAFE_ERRORS = Set.of("VALIDATION_ERROR", "UNAUTHENTICATED", "FORBIDDEN",
            "ACCOUNT_DISABLED", "NOT_FOUND", "STATE_CONFLICT", "UNIQUE_CONFLICT", "PRECONDITION_REQUIRED",
            "REVISION_CONFLICT", "REAUTH_REQUIRED", "SERVICE_UNAVAILABLE", "LAST_ADMIN", "RESOURCE_IN_USE");
    private final OperationLogMapper logs;
    private final FailureAuditWriter failures;
    private final SessionManager sessions;
    private final AuthProperties properties;
    private final Clock clock;

    /** 仅捕获声明的元数据；绝不读取 DTO 内容、响应载荷或异常消息。 */
    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint point, OperationLog operation) throws Throwable {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("操作审计必须在业务写事务内执行");
        }
        SysOperationLog log = new SysOperationLog();
        log.setUserId(sessions.currentUserId());
        log.setOperationType(operation.type());
        log.setOperationTarget(operation.target());
        log.setPermissionCode(operation.permission());
        log.setOperationTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (operation.targetIdArgument() >= 0) {
            log.setTargetId((Long) point.getArgs()[operation.targetIdArgument()]);
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            log.setIpAddress(IpUtils.clientIp(attributes.getRequest(), properties.getTrustedProxies()));
        }
        // 1. 提前登记完成回调，覆盖业务异常、外层回滚以及提交阶段失败。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.setResult(0);
                    if (log.getErrorMessage() == null) {
                        log.setErrorMessage("TRANSACTION_NOT_COMMITTED");
                    }
                    failures.write(log);
                }
            }
        });
        try {
            Object result = point.proceed();
            if (log.getTargetId() == null) {
                log.setTargetId(createdId(result));
            }
            // 2. 必须在提交前写入同一事务，不能异步保存成功审计。
            log.setResult(1);
            logs.insertLog(log);
            return result;
        } catch (Throwable error) {
            log.setErrorMessage(safeError(error));
            throw error;
        }
    }

    /** 仅从四种已知创建响应提取 ID，不通过反射或序列化遍历返回对象。 */
    private Long createdId(Object value) {
        return switch (value) {
            case UserAdminVO user -> user.userId();
            case RoleVO role -> role.roleId();
            case PermissionVO permission -> permission.permissionId();
            case MenuVO menu -> menu.menuId();
            default -> null;
        };
    }

    /** 错误码采用固定白名单，不持久化业务异常中的自由文本。 */
    private String safeError(Throwable error) {
        if (error instanceof BusinessException business && SAFE_ERRORS.contains(business.getCode())) {
            return business.getCode();
        }
        if (error instanceof DuplicateKeyException) {
            return "UNIQUE_CONFLICT";
        }
        return error instanceof DataAccessException ? "SERVICE_UNAVAILABLE" : "INTERNAL_ERROR";
    }
}
