package com.xuan.security;

import cn.dev33.satoken.stp.StpInterface;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.AuthorizationMapper;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import com.xuan.vo.CapabilitiesVO;
import com.xuan.vo.MenuNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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

/** 实时查询授权，角色、权限和菜单在同一读取事务里用同一个快照。 */
@Component
@RequiredArgsConstructor
public class AuthorizationProvider implements StpInterface {
    /** 只有这些初始化系统管理权限参与最后管理员保护，新增字典项不自动加进来。 */
    public static final List<String> ADMIN_PERMISSIONS = List.of("admin:access",
            "sys:user:query", "sys:user:create", "sys:user:update", "sys:user:assignRole",
            "sys:role:query", "sys:role:create", "sys:role:update", "sys:role:delete", "sys:role:grant",
            "sys:menu:query", "sys:menu:create", "sys:menu:update", "sys:menu:delete",
            "sys:permission:query", "sys:permission:create", "sys:permission:update", "sys:permission:delete");
    /** 文章接口已经使用这些固定权限，不能删除；它们不加入最后管理员保护名单。 */
    public static final Set<String> ARTICLE_PERMISSIONS = Set.of("blog:article:query", "blog:article:create",
            "blog:article:update", "blog:article:publish", "blog:article:delete", "blog:article:restore", "blog:file:query");
    /** 这些权限已有分类、标签接口使用，不能删除；不加入最后管理员保护名单。 */
    public static final Set<String> TAXONOMY_PERMISSIONS = Set.of("blog:category:query", "blog:category:create",
            "blog:category:update", "blog:category:delete", "blog:tag:query", "blog:tag:create",
            "blog:tag:update", "blog:tag:delete");
    /** 评论、点赞已经使用这些固定权限，禁止删除，但不扩大最后管理员保护名单。 */
    public static final Set<String> INTERACTION_PERMISSIONS = Set.of("blog:comment:query", "blog:comment:review",
            "blog:comment:moderate", "blog:comment:create", "blog:comment:deleteOwn", "blog:article:like");
    /** 友链和配置已有实际后端入口，禁止删权限，不扩大最后管理员保护名单。 */
    public static final Set<String> SITE_PERMISSIONS = Set.of("blog:link:query", "blog:link:create",
            "blog:link:update", "blog:link:delete", "blog:config:query", "blog:config:update");
    /** 上传和删除已有实际文件入口，禁止删除，但不增加最后管理员必须拥有的能力。 */
    public static final Set<String> FILE_PERMISSIONS = Set.of("blog:file:upload", "blog:file:delete");
    /** 第15..20章固定权限已有实际消费者，不扩展最后管理员保护名单。 */
    public static final Set<String> EXTENDED_PERMISSIONS = Set.of(
            "home:profile:query",
            "home:profile:update",
            "home:social:query",
            "home:social:create",
            "home:social:update",
            "home:social:delete",
            "cv:experience:query",
            "cv:experience:create",
            "cv:experience:update",
            "cv:experience:delete",
            "cv:skill:query",
            "cv:skill:create",
            "cv:skill:update",
            "cv:skill:delete",
            "blog:message:query",
            "blog:message:review",
            "blog:message:moderate",
            "blog:message:create",
            "blog:message:updateOwn",
            "blog:message:deleteOwn",
            "blog:music:query",
            "blog:music:create",
            "blog:music:update",
            "blog:music:delete",
            "blog:rss:query",
            "blog:rss:update",
            "blog:rss:delete",
            "blog:rss:subscribe",
            "blog:rss:unsubscribe",
            "blog:visitor:query",
            "blog:visitor:block",
            "blog:visitor:unblock",
            "blog:visitor:delete",
            "blog:view:query",
            "blog:view:delete",
            "blog:statistics:query",
            "sys:log:query",
            "sys:log:delete");
    private final AuthorizationMapper mapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return capabilities(Long.parseLong(loginId.toString())).permissionCodes();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return mapper.roleCodes(Long.parseLong(loginId.toString()));
    }

    /** 只合并授予当前权限的角色，无关角色的 ALL 不能扩大范围。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CapabilitiesVO capabilities(long userId) {
        // 1. 按权限码合并真正授予它的角色范围，用 TreeMap 让响应按字典序排列。
        Map<String, String> scopes = new TreeMap<>();
        for (AuthorizationMapper.Grant grant : mapper.grants(userId)) {
            scopes.merge(grant.getPermissionCode(), grant.getDataScope(),
                    (left, right) -> "ALL".equals(left) || "ALL".equals(right) ? "ALL" : "SELF");
        }
        // 2. 返回有效角色、权限和各自范围，不能按角色名给通配权限。
        return new CapabilitiesVO(mapper.roleCodes(userId), List.copyOf(scopes.keySet()), scopes);
    }

    /** 拿到范围后，资源 Mapper 还要把归属写进 SQL，这里不能代替 SQL 权限检查。 */
    public String requireScope(long userId, String permission) {
        String scope = capabilities(userId).dataScopes().get(permission);
        if (scope == null) {
            throw new BusinessException(403, "FORBIDDEN", "没有操作权限");
        }
        return scope;
    }

    /** 菜单的整条祖先链都要有效，空目录、循环或错误父类型不能生成路由。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MenuNodeVO> menus(long userId) {
        // 1. 先查后台入口权限，普通 member 不返回后台菜单。
        CapabilitiesVO capabilities = capabilities(userId);
        if (!capabilities.permissionCodes().contains("admin:access")) {
            return List.of();
        }
        // 2. 菜单授权还要和有效权限一起检查；目录只管结构，授权的隐藏项保留。
        Set<String> permissions = new HashSet<>(capabilities.permissionCodes());
        Map<Long, MenuGrant> candidates = new HashMap<>();
        for (MenuGrant menu : mapper.menus(userId)) {
            if ("D".equals(menu.getMenuType()) || permissions.contains(menu.getPermissionCode())) {
                candidates.put(menu.getMenuId(), menu);
            }
        }
        // 3. 每个节点都查完整祖先链，再把合法节点分成根节点和子节点。
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
        // 4. 逐层排序并组装菜单树，没有可访问页面的空目录去掉。
        return build(roots, children);
    }

    private boolean validChain(MenuGrant menu, Map<Long, MenuGrant> candidates) {
        // 1. 沿父链记下走过的节点，发现循环就拒绝这个分支。
        Set<Long> seen = new HashSet<>();
        MenuGrant current = menu;
        while (current != null) {
            if (!seen.add(current.getMenuId())) {
                return false;
            }
            Long parentId = current.getParentId();
            // 2. 根节点可以是目录或页面，按钮必须放在页面下面。
            if (parentId == null) {
                return !"B".equals(current.getMenuType());
            }
            // 3. 每一级祖先都得已授权且启用，父类型也得符合目录、页面、按钮的规则。
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
        // 1. 每层先按 sortOrder 再按 menuId 排，排序值相同时结果也稳定。
        nodes.sort(Comparator.comparing(MenuGrant::getSortOrder).thenComparing(MenuGrant::getMenuId));
        for (MenuGrant node : nodes) {
            // 2. 先组装子树，再看当前目录里还有没有能访问的页面。
            List<MenuNodeVO> nested = build(new ArrayList<>(children.getOrDefault(node.getMenuId(), List.of())), children);
            if ("D".equals(node.getMenuType()) && nested.isEmpty()) {
                continue;
            }
            // 3. 有效的隐藏项和按钮原样保留，前端再按类型生成导航或操作。
            result.add(new MenuNodeVO(node.getMenuId(), node.getParentId(), node.getMenuCode(), node.getMenuName(),
                    node.getMenuType(), node.getRouteName(), node.getPath(), node.getComponent(), node.getIcon(),
                    node.getPermissionId(), node.getSortOrder(), node.getIsVisible(), node.getKeepAlive(),
                    node.getStatus(), node.getCreateTime(), node.getUpdateTime(), node.getPermissionCode(), nested));
        }
        return result;
    }
}
