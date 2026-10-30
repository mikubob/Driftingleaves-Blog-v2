package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.entity.SysPermission;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.PermissionMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IPermissionService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.PermissionProfileVO;
import com.xuan.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 权限字典写入依次锁保护点、升序用户和权限；授权改变时先退出相关会话，再提交数据库。
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, SysPermission> implements IPermissionService {

    private final PermissionMapper permissions;
    private final UserMapper users;
    private final IUserService identities;
    private final AuthorizationProvider authorization;
    private final SessionManager sessions;
    private final JsonMapper json;

    /**
     * 同一只读快照里先查后台入口和业务权限，列表、总数用同样条件。
     *
     * @param query 已校验的分页、字面关键词、模块和状态
     * @return 权限 ID 升序分页，无逐条版本；SELF 不缩小全局字典
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<PermissionVO> listPermissions(PermissionQueryDTO query) {
        requireAdmin("sys:permission:query");
        return PageResult.from(permissions.permissionPage(new Page<>(query.page(), query.pageSize()), query)
                .convert(this::view));
    }

    /**
     * 同一快照里先查权限，再取字典资料和版本，禁用项也保留。
     *
     * @param permissionId 全局权限 ID
     * @return 详情及带引号版本，不存在返回 404
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PermissionProfileVO detail(long permissionId) {
        requireAdmin("sys:permission:query");
        return profile(requirePermission(permissions.selectById(permissionId)));
    }

    /**
     * 保护锁和操作者锁内重查权限，只写允许的字典字段，不自动建立角色关联。
     *
     * @param request 已校验的创建资料及默认值
     * @return 回查数据库审计时间后的字典项；唯一冲突使事务回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "sys:permission", permission = "sys:permission:create")
    public PermissionVO create(PermissionCreateDTO request) {
        lockUsers(null, "sys:permission:create");
        SysPermission permission = new SysPermission();
        permission.setPermissionCode(request.getPermissionCode());
        permission.setPermissionName(request.getPermissionName());
        permission.setModule(request.getModule());
        permission.setDescription(request.getDescription());
        permission.setStatus(request.getStatus());
        permissions.insert(permission);
        return view(permissions.selectById(permission.getPermissionId()));
    }

    /**
     * 依次拿到保护锁、用户锁、权限锁后重查身份和版本，再完整替换资料，关联都保留。
     *
     * @param permissionId 目标权限
     * @param request 全部允许字段，不接受权限码
     * @param revision 详情业务版本；缺失、格式错误、过期分别返回 428、400、412
     * @return 新资料和版本；状态变化撤销全部关联用户会话，保护或 Redis 失败整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "sys:permission", permission = "sys:permission:update", targetIdArgument
            = 0)
    public PermissionProfileVO update(long permissionId, PermissionUpdateDTO request, String revision) {
        // 1. 锁内重查原会话、启用身份、后台和业务权限、版本，不能拿等待前的授权继续写。
        List<Long> affected = lockUsers(permissionId, "sys:permission:update");
        SysPermission permission = permissions.lockById(permissionId);
        requireAdmin("sys:permission:update");
        permission = requirePermission(permission);
        checkRevision(permission, revision);
        boolean changed = !permission.getStatus().equals(request.getStatus());

        // 2. 禁用只让授权暂时无效，不删除角色关联或菜单访问限制。
        permission.setPermissionName(request.getPermissionName());
        permission.setModule(request.getModule());
        permission.setDescription(request.getDescription());
        permission.setStatus(request.getStatus());
        permissions.updatePermission(permission);
        if (changed) {
            // 3. 先确认修改后仍有有效管理员，再撤销所有关联用户的会话，成功后才允许提交。
            if (AuthorizationProvider.ADMIN_PERMISSIONS.contains(permission.getPermissionCode())
                    && users.effectiveAdminCount(AuthorizationProvider.ADMIN_PERMISSIONS) == 0) {
                throw new BusinessException(409, "LAST_ADMIN", "必须保留至少一个有效管理员");
            }
            revoke(affected);
        }
        return profile(permissions.selectById(permissionId));
    }

    /**
     * 同一锁事务里查版本和删除限制，再物理删除，角色权限关联由数据库级联清理。
     *
     * @param permissionId 目标权限
     * @param revision 当前版本；状态限制不能绕过版本检查
     * @throws BusinessException 固定后端权限禁止删除，任意菜单引用返回 RESOURCE_IN_USE；撤销失败回滚全部删除
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "sys:permission", permission = "sys:permission:delete", targetIdArgument
            = 0)
    public void delete(long permissionId, String revision) {
        List<Long> affected = lockUsers(permissionId, "sys:permission:delete");
        SysPermission permission = permissions.lockById(permissionId);
        requireAdmin("sys:permission:delete");
        permission = requirePermission(permission);
        checkRevision(permission, revision);
        if (AuthorizationProvider.ADMIN_PERMISSIONS.contains(permission.getPermissionCode())
                || AuthorizationProvider.ARTICLE_PERMISSIONS.contains(permission.getPermissionCode())
                || AuthorizationProvider.TAXONOMY_PERMISSIONS.contains(permission.getPermissionCode())
                || AuthorizationProvider.INTERACTION_PERMISSIONS.contains(permission.getPermissionCode())
                || AuthorizationProvider.SITE_PERMISSIONS.contains(permission.getPermissionCode())
                || AuthorizationProvider.FILE_PERMISSIONS.contains(permission.getPermissionCode())
                || AuthorizationProvider.EXTENDED_PERMISSIONS.contains(permission.getPermissionCode())) {
            throw new BusinessException(409, "STATE_CONFLICT", "固定后端权限禁止删除");
        }
        if (permissions.menuReferences(permissionId) != 0) {
            throw new BusinessException(409, "RESOURCE_IN_USE", "权限仍被菜单引用");
        }
        // 菜单外键仍是 RESTRICT，只有角色关联允许 CASCADE；撤销会话失败时字典和关联一起回滚。
        permissions.deleteById(permissionId);
        revoke(affected);
    }

    /**
     * 实时查启用账号、原 Token、后台入口和本次业务权限。
     *
     * @param permission 当前 sys:permission 操作权限，SELF 对全局字典无本人语义
     */
    private void requireAdmin(String permission) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, permission);
    }

    /**
     * 写事务先拿固定保护锁，再升序锁操作者及全部关联用户，最后重查身份和权限。
     *
     * @param permissionId 目标权限，创建时为 null，仅锁操作者
     * @param permission 当前操作权限
     * @return 全部受影响用户，包含禁用角色和禁用用户，多角色关联去重
     */
    private List<Long> lockUsers(Long permissionId, String permission) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "管理员保护角色未初始化");
        }
        List<Long> affected = permissionId == null ? List.of() : permissions.userIds(permissionId);
        Set<Long> ids = new TreeSet<>(affected);
        ids.add(sessions.currentUserId());
        for (long id : ids) {
            users.lockById(id);
        }
        requireAdmin(permission);
        return affected;
    }

    /**
     * 全部目标用户锁到后再撤销会话，权限和菜单都实时查库，不沿用旧缓存。
     *
     * @param affected 已去重的关联用户；Redis 失败直接抛出使数据库事务回滚
     */
    private void revoke(List<Long> affected) {
        for (long userId : affected) {
            sessions.revokeAll(userId);
        }
    }

    /**
     * 先检查权限，再查字典项是否存在，禁用项也能取到。
     *
     * @param permission 数据库查询结果
     * @return 存在的实体；不存在抛出 404
     */
    private SysPermission requirePermission(SysPermission permission) {
        if (permission == null) {
            throw new BusinessException(404, "NOT_FOUND", "权限不存在");
        }
        return permission;
    }

    /**
     * 拿到权限行锁后先查单个带引号版本，再写入、查状态限制或撤销会话。
     *
     * @param permission 当前锁定实体
     * @param revision 客户端详情版本，拒绝通配符和多个版本
     */
    private void checkRevision(SysPermission permission, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(permission).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "权限已修改，请重新获取");
        }
    }

    /**
     * 同一快照或锁事务里按固定顺序生成业务版本，时间字段不参与。
     *
     * @param permission 当前实体，编码固定，资料及状态均参与摘要
     * @return 约定资料及带引号的 SHA-256 版本
     */
    private PermissionProfileVO profile(SysPermission permission) {
        PermissionVO value = view(permission);
        String canonical = json.writeValueAsString(Arrays.asList(value.permissionId(), value.permissionCode(),
                value.permissionName(), value.module(), value.description(), value.status()));
        return new PermissionProfileVO(value, "\"" + TokenUtils.digest(canonical) + "\"");
    }

    /**
     * 只返回约定的字典资料，调用方负责用同一快照或行锁读取。
     *
     * @param permission 已确认存在的实体
     * @return 对外字段及数据库维护的 UTC 审计时间
     */
    private PermissionVO view(SysPermission permission) {
        return new PermissionVO(permission.getPermissionId(), permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getModule(), permission.getDescription(), permission.getStatus(),
                permission.getCreateTime(), permission.getUpdateTime());
    }
}
