package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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
import com.xuan.service.AuthorizationService;
import com.xuan.service.IRoleService;
import com.xuan.service.IUserService;
import com.xuan.service.SessionService;
import com.xuan.vo.RoleProfileVO;
import com.xuan.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 角色资料及共同授权事务；保护锁、升序用户锁、角色锁后复核权限，撤销失败整笔回滚。 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, SysRole> implements IRoleService {
    private final RoleMapper roles;
    private final UserMapper users;
    private final IUserService identities;
    private final AuthorizationService authorization;
    private final SessionService sessions;
    private final JsonMapper json;

    /**
     * 在同一只读快照检查双权限并分页，统计和完整关联集合与返回资料一致。
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
     * 在一致快照内读取角色及两个完整授权集合，禁用关联也参与聚合版本。
     * @param roleId 全局目标角色 ID
     * @return 角色详情及带引号版本，不存在时在鉴权后返回 404
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RoleProfileVO detail(long roleId) {
        requireAdmin("sys:role:query");
        return profile(requireRole(roles.selectById(roleId)));
    }

    /**
     * 保护锁和操作者锁内复核双权限，显式创建空授权角色。
     * @param request 已校验的创建白名单，默认值由入口模型确定
     * @return 回查数据库审计时间后的角色；编码冲突回滚，不分配任何权限和菜单
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
     * 保护锁、用户锁和角色锁内完整替换资料；范围或状态变化时撤销全部受影响会话。
     * @param roleId 目标角色
     * @param request 完整资料，编码不可修改
     * @param revision 详情聚合版本；缺失、格式错误和过期分别返回 428、400、412
     * @return 更新资料和版本；最后管理员保护或 Redis 失败时整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleProfileVO update(long roleId, RoleUpdateDTO request, String revision) {
        // 1. 先锁受影响用户再锁目标角色，锁内确认当前身份、双权限和版本。
        List<Long> affected = lockUsers(roleId, "sys:role:update");
        SysRole role = requireRole(roles.lockById(roleId));
        checkRevision(role, revision);
        boolean changed = !role.getDataScope().equals(request.getDataScope())
                || !role.getStatus().equals(request.getStatus());
        // 2. 完整替换允许字段，保留角色码及全部授权关联。
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope());
        role.setStatus(request.getStatus());
        roles.updateRole(role);
        if (changed) {
            // 3. 先检查事务后管理员结果，再撤销所有关联用户会话，失败阻止提交。
            protectAdmin(roleId);
            revoke(affected);
        }
        return profile(roles.selectById(roleId));
    }

    /**
     * 同一锁事务删除自定义角色，数据库级联清理用户、权限、菜单三类关联。
     * @param roleId 目标角色
     * @param revision 当前角色聚合版本，先检查版本再检查内置角色状态约束
     * @throws BusinessException 内置角色禁止删除；撤销失败使角色及全部关联恢复
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long roleId, String revision) {
        List<Long> affected = lockUsers(roleId, "sys:role:delete");
        SysRole role = requireRole(roles.lockById(roleId));
        checkRevision(role, revision);
        if (Set.of("admin", "author", "member").contains(role.getRoleCode())) {
            throw new BusinessException(409, "STATE_CONFLICT", "内置角色禁止删除");
        }
        roles.deleteById(roleId);
        revoke(affected);
    }

    /**
     * 同一事务完整替换权限和菜单，祖先与门禁校验成功后才写入两个集合。
     * @param roleId 目标角色
     * @param request 不重复的显式权限及菜单集合，空数组清空
     * @param revision 角色资料与授权共用的版本
     * @return 含最终祖先集合的新资料和版本；任一校验、保护或撤销失败整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleProfileVO grants(long roleId, RoleGrantDTO request, String revision) {
        // 1. 固定保护锁及升序用户锁之后锁角色，复核身份与聚合版本。
        List<Long> affected = lockUsers(roleId, "sys:role:grant");
        SysRole role = requireRole(roles.lockById(roleId));
        checkRevision(role, revision);
        List<Long> permissions = request.permissionIds().stream().sorted().toList();
        if (!permissions.isEmpty() && roles.lockEnabledPermissions(permissions).size() != permissions.size()) {
            throw new BusinessException(400, "VALIDATION_ERROR", "permissionIds须全部存在且启用");
        }
        List<Long> menus = completeMenus(request.menuIds(), new HashSet<>(permissions));
        // 2. 最终集合相同则保持版本和会话；输入顺序不影响相等判断。
        if (!permissions.equals(roles.permissionIds(roleId)) || !menus.equals(roles.menuIds(roleId))) {
            roles.deletePermissions(roleId);
            roles.deleteMenus(roleId);
            if (!permissions.isEmpty()) {
                roles.insertPermissions(roleId, permissions);
            }
            if (!menus.isEmpty()) {
                roles.insertMenus(roleId, menus);
            }
            // 3. 两类授权共同保护最后管理员，Redis 撤销成功后才提交数据库。
            protectAdmin(roleId);
            revoke(affected);
        }
        return profile(role);
    }

    /**
     * 按当前数据库身份、会话及双权限检查全局角色操作。
     * @param permission 具体 sys:role 操作权限；SELF 不限制全局资源
     */
    private void requireAdmin(String permission) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, permission);
    }

    /**
     * 调用方写事务先取得固定保护锁，再升序锁关联用户和操作者并复核当前身份权限。
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
     * 在已持有结构保护锁的事务内补齐祖先，逐节点检查启用、环、父类型和显式门禁权限。
     * @param selected 已校验格式的菜单 ID 集合
     * @param permissions 本次明确选择且已确认启用的权限，不隐式增加
     * @return 去重并升序的最终菜单集合；任一节点或祖先无效则拒绝全部保存
     */
    private List<Long> completeMenus(List<Long> selected, Set<Long> permissions) {
        Map<Long, SysMenu> nodes = new HashMap<>();
        if (selected.isEmpty()) {
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
     * 固定保护锁内检查 admin 角色变更后的全部初始系统权限与有效管理员。
     * @param roleId 已修改角色；其他角色不影响固定 admin 条件
     * @throws BusinessException 最后管理员将失效，数据库写入必须回滚
     */
    private void protectAdmin(long roleId) {
        if (roleId == 1 && users.effectiveAdminCount(AuthorizationService.ADMIN_PERMISSIONS) == 0) {
            throw new BusinessException(409, "LAST_ADMIN", "必须保留至少一个有效管理员");
        }
    }

    /**
     * 持有全部目标用户锁时逐一撤销所有会话，权限与菜单实时读库，无旧授权缓存。
     * @param affected 已锁定的关联用户；Redis 任一失败直接抛出以回滚数据库
     */
    private void revoke(List<Long> affected) {
        for (long userId : affected) {
            sessions.revokeAll(userId);
        }
    }

    /**
     * 在权限检查之后确认角色存在，不过滤禁用角色。
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
     * 已持有角色锁时检查聚合版本，先于任何状态、关联写入或会话撤销。
     * @param role 当前已锁定角色
     * @param revision 客户端原样携带的单个带引号版本
     */
    private void checkRevision(SysRole role, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(role).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "角色已修改，请重新获取");
        }
    }

    /**
     * 从同一快照或锁事务生成资料及固定顺序的聚合摘要，审计时间不参与版本。
     * @param role 当前角色实体
     * @return 角色资料与带引号 SHA-256 版本，权限或菜单变化也会改变摘要
     */
    private RoleProfileVO profile(SysRole role) {
        RoleVO value = view(role);
        String canonical = json.writeValueAsString(Arrays.asList(value.roleId(), value.roleCode(), value.roleName(),
                value.description(), value.dataScope(), value.status(), value.permissionIds(), value.menuIds()));
        return new RoleProfileVO(value, "\"" + SessionService.digest(canonical) + "\"");
    }

    /**
     * 投影角色资料与排序的完整关联，禁用项仍返回；调用方负责快照或行锁一致性。
     * @param role 当前角色实体
     * @return 对外角色白名单字段
     */
    private RoleVO view(SysRole role) {
        return new RoleVO(role.getRoleId(), role.getRoleCode(), role.getRoleName(), role.getDescription(),
                role.getDataScope(), role.getStatus(), role.getCreateTime(), role.getUpdateTime(),
                roles.permissionIds(role.getRoleId()), roles.menuIds(role.getRoleId()));
    }
}
