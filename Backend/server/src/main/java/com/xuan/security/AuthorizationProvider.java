package com.xuan.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpInterface;
import com.xuan.cache.UserAuthzCache;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import com.xuan.mapper.AuthorizationMapper;
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

/**
 * 用户授权查询，拦截器阶段复用单次请求缓存与 Redis 授权缓存，写操作范围复核保持直查数据库。
 */
@Component
@RequiredArgsConstructor
public class AuthorizationProvider implements StpInterface {

    private static final String REQUEST_CAPABILITIES_PREFIX = "authz:capabilities:";

    /**
     * 只有这些初始化系统管理权限参与最后管理员保护，新增字典项不自动加进来。
     */
    public static final List<String> ADMIN_PERMISSIONS = List.of("admin:access",
            "sys:user:query", "sys:user:create", "sys:user:update", "sys:user:assignRole",
            "sys:role:query", "sys:role:create", "sys:role:update", "sys:role:delete", "sys:role:grant",
            "sys:menu:query", "sys:menu:create", "sys:menu:update", "sys:menu:delete",
            "sys:permission:query", "sys:permission:create", "sys:permission:update", "sys:permission:delete");
    /**
     * 文章接口已经使用这些固定权限，不能删除；它们不加入最后管理员保护名单。
     */
    public static final Set<String> ARTICLE_PERMISSIONS = Set.of("blog:article:query", "blog:article:create",
            "blog:article:update", "blog:article:publish", "blog:article:delete", "blog:article:restore", "blog:article:purge",
            "blog:file:query");
    /**
     * 这些权限已有分类、标签接口使用，不能删除；不加入最后管理员保护名单。
     */
    public static final Set<String> TAXONOMY_PERMISSIONS = Set.of("blog:category:query", "blog:category:create",
            "blog:category:update", "blog:category:delete", "blog:tag:query", "blog:tag:create",
            "blog:tag:update", "blog:tag:delete");
    /**
     * 评论、点赞已经使用这些固定权限，禁止删除，但不扩大最后管理员保护名单。
     */
    public static final Set<String> INTERACTION_PERMISSIONS = Set.of("blog:comment:query", "blog:comment:review",
            "blog:comment:moderate", "blog:comment:create", "blog:comment:deleteOwn", "blog:article:like");
    /**
     * 友链和配置已有实际后端入口，禁止删权限，不扩大最后管理员保护名单。
     */
    public static final Set<String> SITE_PERMISSIONS = Set.of("blog:link:query", "blog:link:create",
            "blog:link:update", "blog:link:delete", "blog:config:query", "blog:config:update");
    /**
     * 上传和删除已有实际文件入口，禁止删除，但不增加最后管理员必须拥有的能力。
     */
    public static final Set<String> FILE_PERMISSIONS = Set.of("blog:file:upload", "blog:file:delete");
    /**
     * 这些固定权限已有接口使用，不能删除，也不加入最后管理员保护名单。
     */
    public static final Set<String> EXTENDED_PERMISSIONS = Set.of(
            "sys:user:delete",
            "site:ownerProfile:query",
            "site:ownerProfile:update",
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
    private final UserAuthzCache userAuthzCache;

    /**
     * 按当前启用的用户、角色和权限，为 Sa-Token 返回有效权限码；同一次请求内复用缓存，避免拦截器重复查询。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return requestCapabilities(Long.parseLong(loginId.toString())).getPermissionCodes();
    }

    /**
     * 返回该用户当前有效的角色码，复用单次请求缓存与 Redis 授权缓存。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return requestCapabilities(Long.parseLong(loginId.toString())).getRoleCodes();
    }

    /**
     * 查询当前用户的有效权限，通过独立缓存组件读取，避免同类自调用绕过代理。
     */
    public CapabilitiesVO capabilities(long userId) {
        return userAuthzCache.loadCapabilities(userId);
    }

    /**
     * 直查数据库获取操作权限范围，确保锁内写操作复核权限不受缓存延迟影响；Mapper 仍需在 SQL 中限制归属。
     */
    public String requireScope(long userId, String permission) {
        String scope = null;
        for (AuthorizationMapper.Grant grant : mapper.grants(userId)) {
            if (!permission.equals(grant.getPermissionCode())) {
                continue;
            }
            if ("ALL".equals(grant.getDataScope())) {
                return "ALL";
            }
            scope = "SELF";
        }
        if (scope == null) {
            throw new BusinessException(403, "FORBIDDEN", "没有操作权限");
        }
        return scope;
    }

    /**
     * 查询本人菜单，全部上级菜单都必须有效，空目录、循环或父类型错误的菜单不生成路由。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MenuNodeVO> menus(long userId) {
        // 1. 先查后台入口权限，普通 member 不返回后台菜单。
        CapabilitiesVO capabilities = userAuthzCache.loadCapabilities(userId);
        if (!capabilities.getPermissionCodes().contains("admin:access")) {
            return List.of();
        }

        // 2. 菜单授权还要和有效权限一起检查；目录只管结构，授权的隐藏项保留。
        Set<String> permissions = new HashSet<>(capabilities.getPermissionCodes());
        Map<Long, MenuGrant> candidates = new HashMap<>();
        for (MenuGrant menu : mapper.menus(userId)) {
            if ("D".equals(menu.getMenuType()) || permissions.contains(menu.getPermissionCode())) {
                candidates.put(menu.getMenuId(), menu);
            }
        }

        // 3. 检查每个菜单的全部上级菜单，再将有效菜单分为根菜单和子菜单。
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

    /**
     * 优先从当前请求的 SaHolder 存储复用授权结果，未命中时再读取 Redis 授权缓存。
     */
    private CapabilitiesVO requestCapabilities(long userId) {
        if (!SaHolder.getContext().isValid()) {
            return userAuthzCache.loadCapabilities(userId);
        }
        String key = REQUEST_CAPABILITIES_PREFIX + userId;
        Object cached = SaHolder.getStorage().get(key);
        if (cached instanceof CapabilitiesVO capabilities) {
            return capabilities;
        }
        CapabilitiesVO loaded = userAuthzCache.loadCapabilities(userId);
        SaHolder.getStorage().set(key, loaded);
        return loaded;
    }

    /**
     * 检查全部上级菜单的授权、启用状态和类型，出现循环时拒绝该分支。
     */
    private boolean validChain(MenuGrant menu, Map<Long, MenuGrant> candidates) {
        // 1. 逐级检查上级菜单并记录已访问的节点，发现循环时拒绝该分支。
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

    /**
     * 按排序值和 ID 组装菜单树，移除没有可访问页面的目录。
     */
    private List<MenuNodeVO> build(List<MenuGrant> nodes, Map<Long, List<MenuGrant>> children) {
        List<MenuNodeVO> result = new ArrayList<>();

        // 1. 每层先按 sortOrder 再按 menuId 排，排序值相同时结果也稳定。
        nodes.sort(Comparator.comparing(MenuGrant::getSortOrder).thenComparing(MenuGrant::getMenuId));
        for (MenuGrant node : nodes) {
            // 2. 先组装子树，再看当前目录里还有没有能访问的页面。
            List<MenuNodeVO> nested = build(new ArrayList<>(children.getOrDefault(node.getMenuId(), List.of())),
                    children);
            if ("D".equals(node.getMenuType()) && nested.isEmpty()) {
                continue;
            }

            // 3. 有效的隐藏项和按钮原样保留，前端再按类型生成导航或操作。
            result.add(MenuNodeVO.builder()
                    .menuId(node.getMenuId())
                    .parentId(node.getParentId())
                    .menuCode(node.getMenuCode())
                    .menuName(node.getMenuName())
                    .menuType(node.getMenuType())
                    .routeName(node.getRouteName())
                    .path(node.getPath())
                    .component(node.getComponent())
                    .icon(node.getIcon())
                    .permissionId(node.getPermissionId())
                    .sortOrder(node.getSortOrder())
                    .isVisible(node.getIsVisible())
                    .keepAlive(node.getKeepAlive())
                    .status(node.getStatus())
                    .createTime(node.getCreateTime())
                    .updateTime(node.getUpdateTime())
                    .permissionCode(node.getPermissionCode())
                    .children(nested)
                    .build());
        }
        return result;
    }
}
