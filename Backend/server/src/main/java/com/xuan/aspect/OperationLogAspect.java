package com.xuan.aspect;

import cn.dev33.satoken.exception.NotLoginException;
import com.xuan.annotation.OperationLog;
import com.xuan.audit.FailureAuditWriter;
import com.xuan.entity.BlogCategory;
import com.xuan.entity.BlogFriendLink;
import com.xuan.entity.BlogMusic;
import com.xuan.entity.BlogTag;
import com.xuan.entity.CvExperience;
import com.xuan.entity.CvSkill;
import com.xuan.entity.HomeProfile;
import com.xuan.entity.HomeSocialLink;
import com.xuan.entity.SysOperationLog;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.OperationLogMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.properties.AuthProperties;
import com.xuan.session.SessionManager;
import com.xuan.utils.IpUtils;
import com.xuan.vo.ArticleProfileVO;
import com.xuan.vo.FileVO;
import com.xuan.vo.MenuVO;
import com.xuan.vo.MessagePrivateVO;
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

/**
 * 操作日志在业务写事务里保存，成功日志写失败就回滚；失败日志等回滚结束后另写。
 */
@Aspect
@Component
@Order(200)
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final Set<String> SAFE_ERRORS = Set.of("VALIDATION_ERROR", "UNAUTHENTICATED", "FORBIDDEN",
            "ACCOUNT_DISABLED", "NOT_FOUND", "STATE_CONFLICT", "UNIQUE_CONFLICT", "PRECONDITION_REQUIRED",
            "REVISION_CONFLICT", "REAUTH_REQUIRED", "SERVICE_UNAVAILABLE", "LAST_ADMIN", "RESOURCE_IN_USE",
            "PAYLOAD_TOO_LARGE");
    private final OperationLogMapper logs;
    private final FailureAuditWriter failures;
    private final SessionManager sessions;
    private final AuthProperties properties;
    private final Clock clock;

    /** 这里只取注解声明的操作资料，不读 DTO 内容、响应正文或异常消息。 */
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

        // 1. 先登记事务结束回调，业务、外层事务或提交阶段出错都能记录失败。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * {@inheritDoc}
             */
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

            // 2. 成功日志要在提交前写进同一事务，不能异步保存。
            log.setResult(1);
            logs.insertLog(log);
            return result;
        } catch (Throwable error) {
            log.setErrorMessage(safeError(error));
            throw error;
        }
    }

    /** 只从已知的创建结果里取 ID，不用反射或序列化遍历整个对象。 */
    private Long createdId(Object value) {
        if (value instanceof ResourceVersion<?> resource) {
            return createdId(resource.value());
        }
        return switch (value) {
            case UserAdminVO user -> user.userId();
            case RoleVO role -> role.roleId();
            case PermissionVO permission -> permission.permissionId();
            case MenuVO menu -> menu.menuId();
            case ArticleProfileVO article -> article.article().getArticleId();
            case BlogCategory category -> category.getCategoryId();
            case BlogTag tag -> tag.getTagId();
            case BlogFriendLink link -> link.getLinkId();
            case FileVO file -> file.fileId();
            case MessagePrivateVO row -> row.messageId();
            case HomeProfile row -> row.getProfileId();
            case HomeSocialLink row -> row.getSocialId();
            case CvExperience row -> row.getExperienceId();
            case CvSkill row -> row.getSkillId();
            case BlogMusic row -> row.getMusicId();
            default -> null;
        };
    }

    /** 只记录名单里的固定错误码，不能把异常自由文本存进日志。 */
    private String safeError(Throwable error) {
        if (error instanceof NotLoginException) {
            return "UNAUTHENTICATED";
        }
        if (error instanceof BusinessException business && SAFE_ERRORS.contains(business.getCode())) {
            return business.getCode();
        }
        if (error instanceof DuplicateKeyException) {
            return "UNIQUE_CONFLICT";
        }
        return error instanceof DataAccessException ? "SERVICE_UNAVAILABLE" : "INTERNAL_ERROR";
    }
}
