package com.xuan.service;

import cn.dev33.satoken.stp.StpInterface;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.AuthorizationMapper;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import com.xuan.vo.CapabilitiesVO;
import com.xuan.vo.MenuNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 实时授权提供者；同一读取事务中的角色、权限和菜单使用一致快照。 */
@Service
@RequiredArgsConstructor
public class AuthorizationService implements StpInterface {
    /** 与初始化 SQL 固定权限一致，新增字典项不自动纳入管理员保护条件。 */
    public static final List<String> ADMIN_PERMISSIONS = List.of("admin:access",
            "sys:user:query", "sys:user:create", "sys:user:update", "sys:user:assignRole",
            "sys:role:query", "sys:role:create", "sys:role:update", "sys:role:delete", "sys:role:grant",
            "sys:menu:query", "sys:menu:create", "sys:menu:update", "sys:menu:delete",
            "sys:permission:query", "sys:permission:create", "sys:permission:update", "sys:permission:delete");
    private final AuthorizationMapper mapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return capabilities(Long.parseLong(loginId.toString())).permissionCodes();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return mapper.roleCodes(Long.parseLong(loginId.toString()));
    }

    /** 只合并授予该权限的角色，不能由无关 ALL 角色扩大范围。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CapabilitiesVO capabilities(long userId) {
        // 1. 按权限码合并明确授予该权限的角色范围，TreeMap 保证响应字典序。
        Map<String, String> scopes = new TreeMap<>();
        for (AuthorizationMapper.Grant grant : mapper.grants(userId)) {
            scopes.merge(grant.getPermissionCode(), grant.getDataScope(),
                    (left, right) -> "ALL".equals(left) || "ALL".equals(right) ? "ALL" : "SELF");
        }
        // 2. 返回有效角色、权限和各自范围，不按角色名称赋予通配权限。
        return new CapabilitiesVO(mapper.roleCodes(userId), List.copyOf(scopes.keySet()), scopes);
    }

    /** 后续资源 Mapper 需使用返回范围生成归属条件；本方法不代替 SQL 授权。 */
    public String requireScope(long userId, String permission) {
        String scope = capabilities(userId).dataScopes().get(permission);
        if (scope == null) {
            throw new BusinessException(403, "FORBIDDEN", "没有操作权限");
        }
        return scope;
    }

    /** 仅返回完整授权祖先链；空目录、环及错误父类型均不能产生路由。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MenuNodeVO> menus(long userId) {
        // 1. 先检查后台入口权限，普通 member 不获得后台菜单。
        CapabilitiesVO capabilities = capabilities(userId);
        if (!capabilities.permissionCodes().contains("admin:access")) {
            return List.of();
        }
        // 2. 菜单授权与有效权限求交；目录仅提供结构，隐藏项仍保留。
        Set<String> permissions = new HashSet<>(capabilities.permissionCodes());
        Map<Long, MenuGrant> candidates = new HashMap<>();
        for (MenuGrant menu : mapper.menus(userId)) {
            if ("D".equals(menu.getMenuType()) || permissions.contains(menu.getPermissionCode())) {
                candidates.put(menu.getMenuId(), menu);
            }
        }
        // 3. 检查每个节点的完整祖先链，再将合法节点分组为根和子节点。
        Map<Long, List<MenuGrant>> children = new HashMap<>();
        List<MenuGrant> roots = new ArrayList<>();
        for (MenuGrant menu : candidates.values()) {
            if (validChain(menu, candidates)) {
                if (menu.getParentId() == null) {
                    roots.add(menu);
                } else {
                    children.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
                }
            }
        }
        // 4. 逐层排序并递归构树，剔除没有可访问页面的空目录。
        return build(roots, children);
    }

    private boolean validChain(MenuGrant menu, Map<Long, MenuGrant> candidates) {
        // 1. 沿父链记录已访问节点，遇到环立即拒绝该分支。
        Set<Long> seen = new HashSet<>();
        MenuGrant current = menu;
        while (current != null) {
            if (!seen.add(current.getMenuId())) {
                return false;
            }
            Long parentId = current.getParentId();
            // 2. 根可以是目录或页面，按钮必须依附页面。
            if (parentId == null) {
                return !"B".equals(current.getMenuType());
            }
            // 3. 每级祖先都必须已授权且启用，并符合目录、页面、按钮的父类型约束。
            MenuGrant parent = candidates.get(parentId);
            if (parent == null || !("B".equals(current.getMenuType())
                    ? "M".equals(parent.getMenuType()) : "D".equals(parent.getMenuType()))) {
                return false;
            }
            current = parent;
        }
        return false;
    }

    private List<MenuNodeVO> build(List<MenuGrant> nodes, Map<Long, List<MenuGrant>> children) {
        List<MenuNodeVO> result = new ArrayList<>();
        // 1. 每层都按 sortOrder、menuId 排序，保证同排序值下结果稳定。
        nodes.sort(Comparator.comparing(MenuGrant::getSortOrder).thenComparing(MenuGrant::getMenuId));
        for (MenuGrant node : nodes) {
            // 2. 先构造子树，再判断当前目录是否仍包含可访问页面。
            List<MenuNodeVO> nested = build(new ArrayList<>(children.getOrDefault(node.getMenuId(), List.of())), children);
            if ("D".equals(node.getMenuType()) && nested.isEmpty()) {
                continue;
            }
            // 3. 保留有效隐藏项和按钮的原始属性，由前端按类型生成导航或操作。
            result.add(new MenuNodeVO(node.getMenuId(), node.getParentId(), node.getMenuCode(), node.getMenuName(),
                    node.getMenuType(), node.getRouteName(), node.getPath(), node.getComponent(), node.getIcon(),
                    node.getPermissionId(), node.getSortOrder(), node.getIsVisible(), node.getKeepAlive(),
                    node.getStatus(), node.getCreateTime(), node.getUpdateTime(), node.getPermissionCode(), nested));
        }
        return result;
    }
}
