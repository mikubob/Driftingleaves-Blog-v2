package com.xuan.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.RoleCreateDTO;
import com.xuan.dto.RoleGrantDTO;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.entity.SysMenu;
import com.xuan.entity.SysRole;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.RoleMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IRoleService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.RoleProfileVO;
import com.xuan.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 角色资料和授权一起修改，依次拿保护锁、升序用户锁、角色锁后重查权限；撤销会话失败就整笔回滚。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, SysRole> implements IRoleService {

    private final RoleMapper roles;
    private final UserMapper users;
    private final IUserService identities;
    private final AuthorizationProvider authorization;
    private final SessionManager sessions;
    private final JsonMapper json;

    /**
     * 分页查询角色，在同一个只读事务中先检查后台入口和查询权限，确保总数、全部关联和返回资料一致。
     *
     * @param query 已校验的页码、页大小、字面关键词和状态
     * @return 按角色 ID 升序的分页，不提供逐条版本
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<RoleVO> listRoles(RoleQueryDTO query) {
        requireAdmin("sys:role:query");
        return PageResult.from(roles.rolePage(new Page<>(query.page(), query.pageSize()), query).convert(this::view));
    }

    /**
     * 根据id查询角色详情，在同一个只读事务中读取全部权限和菜单，禁用的关联也参与版本计算。
     *
     * @param roleId 全局目标角色 ID
     * @return 角色详情及带引号版本，不存在时在权限检查后返回 404
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RoleProfileVO detail(long roleId) {
        requireAdmin("sys:role:query");
        return profile(requireRole(roles.selectById(roleId)));
    }

    /**
     * 保护锁和操作者锁内重查后台及业务权限，新角色创建时不带任何授权。
     *
     * @param request 已校验的创建字段，默认值由入口模型确定
     * @return 回查数据库审计时间后的角色；编码冲突回滚，不分配任何权限和菜单
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "sys:role", permission = "sys:role:create")
    public RoleVO create(RoleCreateDTO request) {
        lockUsers(null, "sys:role:create");
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope());
        role.setStatus(request.getStatus());
        roles.insert(role);
        return view(roles.selectById(role.getRoleId()));
    }

    /**
     * 保护锁、用户锁、角色锁内完整替换资料，范围或状态改变时撤销全部关联会话。
     *
     * @param roleId 目标角色
     * @param request 完整资料，编码不可修改
     * @param revision 详情整体版本；缺失、格式错误和过期分别返回 428、400、412
     * @return 更新资料和版本；最后管理员保护或 Redis 失败时整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "sys:role", permission = "sys:role:update", targetIdArgument = 0)
    public RoleProfileVO update(long roleId, RoleUpdateDTO request, String revision) {
        // 1. 先锁受影响用户，再锁角色；锁内重查当前身份、后台和业务权限、版本。
        List<Long> affected = lockUsers(roleId, "sys:role:update");
        SysRole role = roles.lockById(roleId);
        requireAdmin("sys:role:update");
        role = requireRole(role);
        checkRevision(role, revision);
        boolean changed = !role.getDataScope().equals(request.getDataScope());

        // 2. 完整替换允许修改的字段，角色码和全部授权关联保留。
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope());
        roles.updateRole(role);
        if (changed) {
            // 3. 先确认修改后仍有有效管理员，再撤销所有关联用户会话，失败就不能提交。
            protectAdmin(roleId);
            revoke(affected);
        }
        return profile(roles.selectById(roleId));
    }

    /**
     * 权限和菜单在同一事务完整替换，祖先和访问权限全查过后才写两个集合。
     *
     * @param roleId 目标角色
     * @param request 不重复的明确权限及菜单集合，空数组清空
     * @param revision 角色资料与授权共用的版本
     * @return 含最终祖先集合的新资料和版本；任一校验、保护或撤销失败整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "grant", target = "sys:role", permission = "sys:role:grant", targetIdArgument = 0)
    public RoleProfileVO grants(long roleId, RoleGrantDTO request, String revision) {
        // 1. 固定保护锁、升序用户锁之后再锁角色，重新检查身份和整体版本。
        List<Long> affected = lockUsers(roleId, "sys:role:grant");
        SysRole role = roles.lockById(roleId);
        requireAdmin("sys:role:grant");
        role = requireRole(role);
        checkRevision(role, revision);
        List<Long> permissions = request.permissionIds().stream().sorted().toList();
        if (CollUtil.isNotEmpty(permissions) && roles.lockEnabledPermissions(permissions).size() != permissions.size()) {
            throw new BusinessException(400, "VALIDATION_ERROR", "permissionIds须全部存在且启用");
        }
        List<Long> menus = completeMenus(request.menuIds(), new HashSet<>(permissions));
        // 权限行锁也可能等待，拿齐锁后再确认原 Token 和本次授权权限。
        requireAdmin("sys:role:grant");

        // 2. 最终集合没变就保留版本和会话，不能因为输入顺序变了就当作修改。
        if (!permissions.equals(roles.permissionIds(roleId)) || !menus.equals(roles.menuIds(roleId))) {
            roles.deletePermissions(roleId);
            roles.deleteMenus(roleId);
            if (CollUtil.isNotEmpty(permissions)) {
                roles.insertPermissions(roleId, permissions);
            }
            if (CollUtil.isNotEmpty(menus)) {
                roles.insertMenus(roleId, menus);
            }

            // 3. 两类授权一起检查最后管理员，Redis 撤销会话成功后才提交数据库。
            protectAdmin(roleId);
            revoke(affected);
        }
        return profile(role);
    }

    /**
     * 按数据库当前身份、会话、后台入口和业务权限检查角色操作。
     *
     * @param permission 具体 sys:role 操作权限；SELF 不限制全局资源
     */
    private void requireAdmin(String permission) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, permission);
    }

    /**
     * 写事务先拿固定保护锁，再升序锁关联用户和操作者，最后重查身份和权限。
     *
     * @param roleId 目标角色；创建时为 null，只锁操作者
     * @param permission 当前操作权限
     * @return 全部关联用户 ID，包含禁用用户，供提交前撤销所有会话
     */
    private List<Long> lockUsers(Long roleId, String permission) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "管理员保护角色未初始化");
        }
        List<Long> affected = roleId == null ? List.of() : roles.userIds(roleId);
        Set<Long> ids = new TreeSet<>(affected);
        ids.add(sessions.currentUserId());
        for (long id : ids) {
            users.lockById(id);
        }
        requireAdmin(permission);
        return affected;
    }

    /**
     * 补齐上级菜单，持有结构锁时逐个检查启用状态、循环、父类型和实际授予的权限。
     *
     * @param selected 已校验的格式的菜单 ID 集合
     * @param permissions 本次明确选择且已确认启用的权限，不隐式增加
     * @return 去重并升序的最终菜单集合；任一节点或祖先无效则拒绝全部保存
     */
    private List<Long> completeMenus(List<Long> selected, Set<Long> permissions) {
        Map<Long, SysMenu> nodes = new HashMap<>();
        if (CollUtil.isEmpty(selected)) {
            return List.of();
        }
        roles.allMenus().forEach(node -> nodes.put(node.getMenuId(), node));
        Set<Long> result = new TreeSet<>();
        for (long id : selected) {
            Set<Long> chain = new HashSet<>();
            SysMenu node = nodes.get(id);
            while (true) {
                if (node == null || node.getStatus() != 1 || !chain.add(node.getMenuId())
                        || (!"D".equals(node.getMenuType()) && !permissions.contains(node.getPermissionId()))) {
                    throw new BusinessException(400, "VALIDATION_ERROR", "菜单及祖先须存在、启用且显式配置门禁权限");
                }
                result.add(node.getMenuId());
                if (node.getParentId() == null) {
                    if ("B".equals(node.getMenuType())) {
                        throw new BusinessException(400, "VALIDATION_ERROR", "按钮必须属于页面");
                    }
                    break;
                }
                SysMenu parent = nodes.get(node.getParentId());
                if (parent == null || !("B".equals(node.getMenuType())
                        ? "M".equals(parent.getMenuType()) : "D".equals(parent.getMenuType()))) {
                    throw new BusinessException(400, "VALIDATION_ERROR", "菜单祖先类型无效");
                }
                node = parent;
            }
        }
        return List.copyOf(result);
    }

    /**
     * 固定保护锁内检查修改后的 admin 角色和系统管理权限，确保仍有有效管理员。
     *
     * @param roleId 已修改角色；其他角色不影响固定 admin 条件
     * @throws BusinessException 最后管理员将失效，数据库写入必须回滚
     */
    private void protectAdmin(long roleId) {
        if (roleId == 1 && users.effectiveAdminCount(AuthorizationProvider.ADMIN_PERMISSIONS) == 0) {
            throw new BusinessException(409, "LAST_ADMIN", "必须保留至少一个有效管理员");
        }
    }

    /**
     * 锁住全部目标用户后逐个撤销所有会话，权限和菜单实时查库，不用旧授权缓存。
     *
     * @param affected 已锁定的关联用户；Redis 任一失败直接抛出以回滚数据库
     */
    private void revoke(List<Long> affected) {
        for (long userId : affected) {
            sessions.revokeAll(userId);
        }
    }

    /**
     * 先检查权限，再确认角色是否存在，禁用角色也能取到。
     *
     * @param role 当前查询结果
     * @return 存在的角色实体
     */
    private SysRole requireRole(SysRole role) {
        if (role == null) {
            throw new BusinessException(404, "NOT_FOUND", "角色不存在");
        }
        return role;
    }

    /**
     * 拿到角色锁后先查整体版本，再查状态、写关联或撤销会话。
     *
     * @param role 当前已锁定角色
     * @param revision 客户端原样携带的单个带引号版本
     */
    private void checkRevision(SysRole role, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "尚未取得编辑版本，请重新打开详情后再操作。");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "编辑版本无效，请保留当前输入，重新打开详情后再操作。");
        }
        if (!revision.equals(profile(role).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "角色已修改，请重新获取");
        }
    }

    /**
     * 同一个只读事务或加锁事务中取资料并按固定顺序计算整体版本，不包含时间字段。
     *
     * @param role 当前角色实体
     * @return 角色资料与带引号 SHA-256 版本，权限或菜单变化也会改变摘要
     */
    private RoleProfileVO profile(SysRole role) {
        RoleVO value = view(role);
        String canonical = json.writeValueAsString(Arrays.asList(value.roleId(), value.roleCode(), value.roleName(),
                value.description(), value.dataScope(), value.status(), value.permissionIds(), value.menuIds()));
        return new RoleProfileVO(value, "\"" + TokenUtils.digest(canonical) + "\"");
    }

    /**
     * 返回角色资料和排序后的完整关联，禁用项也保留；调用方要在同一个只读事务或行锁内读取。
     *
     * @param role 当前角色实体
     * @return 对外约定的角色字段
     */
    private RoleVO view(SysRole role) {
        return new RoleVO(role.getRoleId(), role.getRoleCode(), role.getRoleName(), role.getDescription(),
                role.getDataScope(), role.getStatus(), role.getCreateTime(), role.getUpdateTime(),
                roles.permissionIds(role.getRoleId()), roles.menuIds(role.getRoleId()));
    }

    /** 批量删除角色，先检查整批目标，删除后撤销所有受影响用户的会话。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "sys:role", permission = "sys:role:delete")
    public void deleteBatch(List<BatchItemDTO> items) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "管理员保护角色未初始化");
        }
        requireAdmin("sys:role:delete");
        var sorted = BatchUtils.sorted(items);
        Set<Long> affected = new TreeSet<>();
        for (var item : sorted) {
            affected.addAll(roles.userIds(IdUtils.parse(item.id())));
        }
        Set<Long> lockedUsers = new TreeSet<>(affected);
        lockedUsers.add(sessions.currentUserId());
        for (long id : lockedUsers) {
            users.lockById(id);
        }
        for (var item : sorted) {
            long roleId = IdUtils.parse(item.id());
            String revision = item.revision();
            SysRole role = roles.lockById(roleId);
            requireAdmin("sys:role:delete");
            role = requireRole(role);
            checkRevision(role, revision);
            if (Set.of("admin", "author", "member").contains(role.getRoleCode())) {
                throw new BusinessException(409, "STATE_CONFLICT", "内置角色禁止删除");
            }
        }
        for (var item : sorted) {
            roles.deleteById(IdUtils.parse(item.id()));
        }
        if (users.effectiveAdminCount(AuthorizationProvider.ADMIN_PERMISSIONS) == 0) {
            throw new BusinessException(409, "LAST_ADMIN", "必须保留至少一个有效管理员");
        }
        revoke(new ArrayList<>(affected));
    }

    /** 批量切换状态，先检查整批目标，再按最终状态保护管理员，最后撤销受影响用户会话。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    @OperationLog(type = "batch-status", target = "sys:role", permission = "sys:role:update")
    public BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "管理员保护角色未初始化");
        }
        requireAdmin("sys:role:update");
        var sorted = BatchUtils.sorted(items);
        Map<Long, List<Long>> related = new HashMap<>();
        Set<Long> lockedUsers = new TreeSet<>();
        lockedUsers.add(sessions.currentUserId());
        for (BatchItemDTO item : sorted) {
            long id = IdUtils.parse(item.id());
            List<Long> userIds = roles.userIds(id);
            related.put(id, userIds);
            lockedUsers.addAll(userIds);
        }
        for (long userId : lockedUsers) {
            users.lockById(userId);
        }
        List<SysRole> rows = new ArrayList<>();
        for (BatchItemDTO item : sorted) {
            SysRole row = roles.lockById(IdUtils.parse(item.id()));
            requireAdmin("sys:role:update");
            row = requireRole(row);
            checkRevision(row, item.revision());
            rows.add(row);
        }

        Set<Long> changed = new TreeSet<>();
        Set<Long> revoked = new TreeSet<>();
        Map<Long, String> versions = new HashMap<>();
        for (SysRole row : rows) {
            long id = row.getRoleId();
            if (row.getStatus() != status) {
                roles.updateStatus(id, status);
                row.setStatus(status);
                changed.add(id);
                revoked.addAll(related.get(id));
            }
            versions.put(id, profile(row).revision());
        }
        if (changed.contains(1L) && users.effectiveAdminCount(AuthorizationProvider.ADMIN_PERMISSIONS) == 0) {
            throw new BusinessException(409, "LAST_ADMIN", "必须保留至少一个有效管理员");
        }
        // 版本和整批校验已完成，撤销本人的会话后不再重查当前 Token。
        revoke(new ArrayList<>(revoked));
        return BatchUtils.versions(items, versions);
    }

}
