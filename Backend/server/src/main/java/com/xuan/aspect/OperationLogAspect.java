package com.xuan.aspect;

import cn.dev33.satoken.exception.NotLoginException;
import com.xuan.annotation.OperationLog;
import com.xuan.audit.FailureAuditWriter;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.ArticleWriteDTO;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.dto.ConfigWriteDTO;
import com.xuan.dto.ExperienceCreateDTO;
import com.xuan.dto.ExperienceWriteDTO;
import com.xuan.dto.HomeProfileWriteDTO;
import com.xuan.dto.LinkCreateDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.MusicCreateDTO;
import com.xuan.dto.MusicWriteDTO;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.dto.RoleCreateDTO;
import com.xuan.dto.RoleGrantDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.dto.SkillCreateDTO;
import com.xuan.dto.SkillWriteDTO;
import com.xuan.dto.SocialCreateDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.dto.TagWriteDTO;
import com.xuan.dto.UserRolesDTO;
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
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/**
 * 记录操作日志，成功日志与业务一起提交，写入失败就回滚；失败日志在回滚后另写。
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
    // 明确列出业务字段，新增 DTO 或字段时需要同步登记，不能自动把凭据带进日志。
    private static final Map<Class<?>, Set<String>> DATA_FIELDS = Map.ofEntries(
            Map.entry(AdminUserCreateDTO.class, Set.of("username", "nickname", "email", "avatar", "intro", "status")),
            Map.entry(AdminUserUpdateDTO.class, Set.of("nickname", "email", "avatar", "intro")),
            Map.entry(UserRolesDTO.class, Set.of("roleIds")),
            Map.entry(ArticleWriteDTO.class, Set.of("title", "slug", "summary", "categoryId", "coverUrl", "articleType",
                    "originalUrl", "visibility", "isTop", "allowComment", "markdownContent", "tagIds")),
            Map.entry(CategoryCreateDTO.class, Set.of("categoryName", "slug", "description", "sortOrder", "status")),
            Map.entry(CategoryUpdateDTO.class, Set.of("categoryName", "slug", "description", "sortOrder")),
            Map.entry(TagWriteDTO.class, Set.of("tagName", "slug")),
            Map.entry(RoleCreateDTO.class, Set.of("roleCode", "roleName", "description", "dataScope", "status")),
            Map.entry(RoleUpdateDTO.class, Set.of("roleName", "description", "dataScope")),
            Map.entry(RoleGrantDTO.class, Set.of("permissionIds", "menuIds")),
            Map.entry(PermissionCreateDTO.class, Set.of("permissionCode", "permissionName", "module", "description", "status")),
            Map.entry(PermissionUpdateDTO.class, Set.of("permissionName", "module", "description")),
            Map.entry(MenuCreateDTO.class, Set.of("parentId", "menuCode", "menuName", "menuType", "routeName", "path",
                    "component", "icon", "permissionId", "sortOrder", "isVisible", "keepAlive", "status")),
            Map.entry(MenuUpdateDTO.class, Set.of("parentId", "menuName", "menuType", "routeName", "path",
                    "component", "icon", "permissionId", "sortOrder", "isVisible", "keepAlive")),
            Map.entry(LinkCreateDTO.class, Set.of("linkName", "url", "avatar", "description", "sortOrder", "status")),
            Map.entry(LinkWriteDTO.class, Set.of("linkName", "url", "avatar", "description", "sortOrder")),
            Map.entry(ConfigWriteDTO.class, Set.of("configValue", "description", "isPublic")),
            Map.entry(HomeProfileWriteDTO.class, Set.of("userId", "nickname", "tagline", "description", "avatar",
                    "website", "publicEmail", "githubUrl", "location")),
            Map.entry(SocialCreateDTO.class, Set.of("name", "icon", "url", "sortOrder", "status")),
            Map.entry(SocialWriteDTO.class, Set.of("name", "icon", "url", "sortOrder")),
            Map.entry(ExperienceCreateDTO.class, Set.of("experienceType", "title", "subtitle", "logoUrl", "content",
                    "startDate", "endDate", "sortOrder", "status")),
            Map.entry(ExperienceWriteDTO.class, Set.of("experienceType", "title", "subtitle", "logoUrl", "content",
                    "startDate", "endDate", "sortOrder")),
            Map.entry(SkillCreateDTO.class, Set.of("name", "description", "icon", "sortOrder", "status")),
            Map.entry(SkillWriteDTO.class, Set.of("name", "description", "icon", "sortOrder")),
            Map.entry(MusicCreateDTO.class, Set.of("title", "artist", "durationSeconds", "coverUrl", "audioUrl",
                    "lyricUrl", "lyricType", "sortOrder", "status")),
            Map.entry(MusicWriteDTO.class, Set.of("title", "artist", "durationSeconds", "coverUrl", "audioUrl",
                    "lyricUrl", "lyricType", "sortOrder")),
            Map.entry(MessageEditDTO.class, Set.of("content", "contentFormat", "notifyReply")));
    private static final Set<String> SCALAR_FIELDS = Set.of("id", "userId", "roleId", "permissionId", "menuId",
            "status", "key", "until", "revision", "expected");
    private static final Set<String> CONFIG_FIELDS = Set.of("title", "description", "icp", "publicSecurityRecord",
            "startDate", "enabled", "reviewRequired");
    private final OperationLogMapper logs;
    private final FailureAuditWriter failures;
    private final SessionManager sessions;
    private final AuthProperties properties;
    private final Clock clock;
    private final JsonMapper json;

    /** 记录本次操作，先保存 JSON 参数供成功和失败日志共用，不记录响应或异常原文。 */
    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint point, OperationLog operation) throws Throwable {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("操作审计必须在业务写事务内执行");
        }
        SysOperationLog log = new SysOperationLog();
        log.setUserId(sessions.currentUserId());
        ObjectNode data = operationData(point);
        String operationType = operation.type();
        if (operationType.startsWith("batch-") && data.path("ids").size() == 1) {
            operationType = operationType.substring("batch-".length());
        }
        log.setOperationType(operationType);
        log.setOperationTarget(operation.target());
        log.setPermissionCode(operation.permission());
        log.setOperationTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (operation.targetIdArgument() >= 0) {
            log.setTargetId((Long) point.getArgs()[operation.targetIdArgument()]);
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            log.setIpAddress(IpUtils.clientIp(attributes.getRequest(), properties.getTrustedProxies()));
        }
        log.setOperationData(json.writeValueAsString(data));

        // 1. 先登记事务结束回调，业务、外层事务或提交阶段出错都能记录失败。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务未成功提交时，提交失败日志任务。
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

    /** 提取日志参数，只保存已登记的业务字段，上传只记录文件信息。 */
    private ObjectNode operationData(ProceedingJoinPoint point) {
        String[] names = ((MethodSignature) point.getSignature()).getParameterNames();
        Object[] arguments = point.getArgs();
        ObjectNode data = json.createObjectNode();
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            String name = names[index];
            if (argument instanceof java.util.List<?> selected && ("items".equals(name) || "ids".equals(name))) {
                var ids = json.createArrayNode();
                for (Object value : selected) {
                    if (value instanceof com.xuan.dto.BatchItemDTO item) {
                        ids.add(item.id());
                    } else if (value instanceof String id) {
                        ids.add(id);
                    }
                }
                data.set("ids", ids);
                data.put("selectedCount", Integer.toString(ids.size()));
                continue;
            }
            if (argument instanceof MultipartFile file) {
                ObjectNode metadata = json.createObjectNode();
                metadata.put("originalFilename", file.getOriginalFilename());
                metadata.put("contentType", file.getContentType());
                metadata.put("sizeBytes", Long.toString(file.getSize()));
                data.set(name, metadata);
                continue;
            }
            if (argument != null && DATA_FIELDS.containsKey(argument.getClass())) {
                ObjectNode value = json.valueToTree(argument);
                value.retain(DATA_FIELDS.get(argument.getClass()));
                if (argument instanceof ConfigWriteDTO && value.get("configValue") instanceof ObjectNode config) {
                    ObjectNode fields = json.createObjectNode();
                    for (String field : CONFIG_FIELDS) {
                        JsonNode fieldValue = config.get(field);
                        if (fieldValue != null && fieldValue.isValueNode()) {
                            fields.set(field, fieldValue);
                        }
                    }
                    value.set("configValue", fields);
                }
                data.set(name, value);
                continue;
            }
            if (SCALAR_FIELDS.contains(name)
                    && (argument == null || argument instanceof String
                    || argument instanceof Long || argument instanceof Integer)) {
                data.set(name, json.valueToTree(argument));
            }
        }
        return data;
    }

    /** 从已知的创建结果中获取目标id。 */
    private Long createdId(Object value) {
        if (value == null) {
            return null;
        }
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

    /** 获取可记录的错误码，只保留名单中的固定值，不保存异常原文。 */
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
