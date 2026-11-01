package com.xuan.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.batch.BatchInputs;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.entity.SysMenu;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import com.xuan.mapper.MenuMapper;
import com.xuan.mapper.PermissionMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IMenuService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.MenuProfileVO;
import com.xuan.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 菜单结构和授权写入共用固定检查点，实时读库；改菜单不撤销独立接口权限或会话。
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, SysMenu> implements IMenuService {

    /** 这些键与 SQL 种子的前端注册约定一致，键存在不代表对应页面或业务已经实现。 */
    private static final Set<String> COMPONENTS = Set.of("Layout", "content/article/index", "content/category/index",
            "content/tag/index", "content/comment/index", "content/link/index", "content/file/index",
            "content/message/index", "content/music/index", "content/rss/index", "system/user/index",
            "system/role/index", "system/menu/index", "system/permission/index", "system/config/index",
            "system/log/index", "home/profile/index", "home/social/index", "cv/experience/index", "cv/skill/index",
            "statistics/overview/index", "statistics/visitor/index", "statistics/view/index");
    private static final Set<String> ICONS = Set.of("document", "setting", "home", "chart", "folder", "tag", "chat",
            "link", "picture", "music", "mail", "user", "lock", "menu", "key");
    private final MenuMapper menus;
    private final PermissionMapper permissions;
    private final UserMapper users;
    private final IUserService identities;
    private final AuthorizationProvider authorization;
    private final SessionManager sessions;
    private final JsonMapper json;

    /**
     * 同一快照里先查后台入口和查询权限，再取完整后台树，不套用本人菜单筛选。
     *
     * @return 全状态菜单，保留空目录、隐藏及按钮，逐层按排序值和 ID 升序
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MenuNodeVO> tree() {
        requireAdmin("sys:menu:query");
        Map<Long, List<MenuGrant>> children = new HashMap<>();
        for (MenuGrant node : menus.allMenus()) {
            children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
        }
        return build(null, children);
    }

    /**
     * 同一只读快照里先检查权限，再取资料和版本，禁用项仍能管理。
     *
     * @param menuId 目标菜单
     * @return 详情与带引号版本；不存在返回 404
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MenuProfileVO detail(long menuId) {
        requireAdmin("sys:menu:query");
        return profile(requireMenu(menus.selectById(menuId)));
    }

    /**
     * 先拿保护锁和操作者锁，再查身份、字段、祖先和访问权限，创建后不自动关联角色。
     *
     * @param request 已经格式校验和默认值处理的创建字段
     * @return 回查数据库审计时间的菜单；校验或唯一冲突整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "sys:menu", permission = "sys:menu:create")
    public MenuVO create(MenuCreateDTO request) {
        Map<Long, SysMenu> nodes = lockStructure(null);
        requireAdmin("sys:menu:create");
        SysMenu menu = new SysMenu();
        menu.setParentId(request.getParentId());
        menu.setMenuCode(request.getMenuCode());
        menu.setMenuName(request.getMenuName());
        menu.setMenuType(request.getMenuType());
        menu.setRouteName(request.getRouteName());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setPermissionId(request.getPermissionId());
        menu.setSortOrder(request.getSortOrder());
        menu.setIsVisible(request.getIsVisible());
        menu.setKeepAlive(request.getKeepAlive());
        menu.setStatus(request.getStatus());
        validateFields(menu);
        validateChain(menu, nodes);
        menus.insert(menu);
        return view(menus.selectById(menu.getMenuId()));
    }

    /**
     * 同一个结构锁事务里重查原 Token、后台和业务权限、版本，再完整替换并检查所有受影响后代。
     *
     * @param menuId 目标菜单
     * @param request 全部允许修改的字段，编码和审计字段不可输入
     * @param revision 单个带引号版本；缺失 428、格式错误 400、过期 412
     * @return 新详情及版本；不补角色祖先或新门禁权限，不撤销会话，失败整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "sys:menu", permission = "sys:menu:update", targetIdArgument = 0)
    public MenuProfileVO update(long menuId, MenuUpdateDTO request, String revision) {
        // 1. 依次锁结构检查点、升序用户、目标菜单，按最新提交的树查版本和结构。
        Map<Long, SysMenu> nodes = lockStructure(menuId);
        SysMenu menu = menus.lockById(menuId);
        requireAdmin("sys:menu:update");
        menu = requireMenu(menu);
        checkRevision(menu, revision);
        menu.setParentId(request.getParentId());
        menu.setMenuName(request.getMenuName());
        menu.setMenuType(request.getMenuType());
        menu.setRouteName(request.getRouteName());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setPermissionId(request.getPermissionId());
        menu.setSortOrder(request.getSortOrder());
        menu.setIsVisible(request.getIsVisible());
        menu.setKeepAlive(request.getKeepAlive());
        menu.setStatus(request.getStatus());

        // 2. 把候选菜单放回整棵树里检查，不能引用自己、移到后代或因改类型破坏整个分支。
        validateFields(menu);
        nodes.put(menuId, menu);
        for (long id : descendants(menuId, nodes)) {
            validateChain(nodes.get(id), nodes);
        }

        // 3. 角色关联都保留，下次查询本人菜单时按新结构和访问权限实时计算。
        menus.updateMenu(menu);
        return profile(menus.selectById(menuId));
    }

    /**
     * 结构锁事务里先查权限、锁用户和菜单、查版本，确认没有子节点后再删。
     *
     * @param menuId 目标菜单
     * @param revision 当前详情版本，先于资源引用检查
     * @throws BusinessException 子节点存在返回 RESOURCE_IN_USE；叶子仅级联 role_menu，失败回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "sys:menu", permission = "sys:menu:delete", targetIdArgument = 0)
    public void delete(long menuId, String revision) {
        deleteBatch(List.of(new BatchItemDTO(Long.toString(menuId), revision)));
    }

    /**
     * 实时查启用账号、原 Token、后台入口和菜单业务权限，SELF 不缩小整棵菜单树。
     *
     * @param permission 当前 sys:menu 操作权限
     */
    private void requireAdmin(String permission) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, permission);
    }

    /**
     * 写事务先锁固定检查点，再升序锁操作者和受影响用户；目标节点锁到后，入口还要重查身份和权限。
     *
     * @param menuId 目标菜单；创建为 null，仅锁操作者
     * @return 结构锁保护下最新提交的整树，不持有授权缓存；Redis 身份定位失败阻止写入
     */
    private Map<Long, SysMenu> lockStructure(Long menuId) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "菜单结构检查点未初始化");
        }
        Map<Long, SysMenu> nodes = new LinkedHashMap<>();
        menus.allMenus().forEach(node -> nodes.put(node.getMenuId(), node));
        Set<Long> ids = new TreeSet<>();
        if (menuId != null) {
            ids.addAll(menus.userIds(descendants(menuId, nodes)));
        }
        ids.add(sessions.currentUserId());
        for (long id : ids) {
            users.lockById(id);
        }
        return nodes;
    }

    /**
     * 持有结构锁时找出目标和全部后代，用来锁关联用户、检查类型变化，记录访问过的节点避免循环。
     *
     * @param menuId 分支根 ID
     * @param nodes 已读取的整树
     * @return 包含根的去重 ID 集合，即使目标不存在也保留根以继续标准 404 流程
     */
    private List<Long> descendants(long menuId, Map<Long, SysMenu> nodes) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (SysMenu node : nodes.values()) {
            children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getMenuId());
        }
        Set<Long> found = new TreeSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(menuId);
        while (CollUtil.isNotEmpty(pending)) {
            long id = pending.remove();
            if (found.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return List.copyOf(found);
    }

    /**
     * 检查字段组合、前端允许的键和访问权限是否存在；禁用权限也可保留，不能因此自动放行。
     *
     * @param menu 已完成格式校验和默认值处理的候选菜单，调用方持有结构锁
     * @throws BusinessException 类型形状、路径、组件、图标或权限引用无效时返回 400
     */
    private void validateFields(SysMenu menu) {
        boolean button = "B".equals(menu.getMenuType());
        boolean directory = "D".equals(menu.getMenuType());
        if (button ? menu.getRouteName() != null || menu.getPath() != null || menu.getComponent() != null
                || menu.getIsVisible() != 0 : menu.getRouteName() == null || menu.getPath() == null
                || !COMPONENTS.contains(menu.getComponent() == null ? "" : menu.getComponent())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "菜单路由、组件或可见性不符合类型约束");
        }
        if ((!"M".equals(menu.getMenuType()) && menu.getKeepAlive() != 0)
                || (menu.getIcon() != null && !ICONS.contains(menu.getIcon()))) {
            throw new BusinessException(400, "VALIDATION_ERROR", "缓存或图标不符合菜单约束");
        }
        if (directory ? menu.getPermissionId() != null : menu.getPermissionId() == null
                || permissions.selectById(menu.getPermissionId()) == null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "目录门禁必须为空，页面和按钮须引用已存在权限");
        }
        if (!button) {
            validatePath(menu.getPath());
        }
    }

    /**
     * 内部路由只收 ASCII，解码后也要查，协议、父目录、查询、片段、反斜线和控制字符都拒绝。
     *
     * @param path 已确认非空的 D/M 路径
     * @throws BusinessException 非法 URI 或路径返回 400；不将路由解释为 API 授权规则
     */
    private void validatePath(String path) {
        String decoded;
        try {
            decoded = URI.create(path).getPath();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "VALIDATION_ERROR", "内部路由格式无效");
        }
        for (String value : Arrays.asList(path, decoded)) {
            if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("..")
                    || value.contains("\\") || value.contains(":") || value.contains("?") || value.contains("#")
                    || value.chars().anyMatch(character -> character < 33 || character > 126)) {
                throw new BusinessException(400, "VALIDATION_ERROR", "必须为合法 ASCII 内部路由");
            }
        }
    }

    /**
     * 结构锁内查整条祖先链和父类型；启停不改变结构是否合法，也不要求父节点必须启用。
     *
     * @param menu 候选节点或受影响后代
     * @param nodes 包含候选变更的整树
     * @throws BusinessException 自引用、移入后代、无效父节点或 D/M/B 层级错误均返回 400
     */
    private void validateChain(SysMenu menu, Map<Long, SysMenu> nodes) {
        Set<Long> seen = new HashSet<>();
        SysMenu node = menu;
        while (true) {
            if (!seen.add(node.getMenuId())) {
                throw new BusinessException(400, "VALIDATION_ERROR", "菜单不能自引用或移入后代");
            }
            if (node.getParentId() == null) {
                if ("B".equals(node.getMenuType())) {
                    throw new BusinessException(400, "VALIDATION_ERROR", "按钮必须属于页面");
                }
                return;
            }
            SysMenu parent = nodes.get(node.getParentId());
            if (parent == null || !("B".equals(node.getMenuType())
                    ? "M".equals(parent.getMenuType()) : "D".equals(parent.getMenuType()))) {
                throw new BusinessException(400, "VALIDATION_ERROR", "父节点不存在或类型无效");
            }
            node = parent;
        }
    }

    /**
     * 调用方持有同一快照，节点已按排序值和 ID 排好，这里逐层组装完整树。
     *
     * @param parentId 当前父 ID，根为 null
     * @param children 完整节点的父子分组
     * @return 保留全部状态、空目录和按钮的 MenuNode 列表
     */
    private List<MenuNodeVO> build(Long parentId, Map<Long, List<MenuGrant>> children) {
        List<MenuNodeVO> result = new ArrayList<>();
        for (MenuGrant node : children.getOrDefault(parentId, List.of())) {
            result.add(new MenuNodeVO(node.getMenuId(), node.getParentId(), node.getMenuCode(), node.getMenuName(),
                    node.getMenuType(), node.getRouteName(), node.getPath(), node.getComponent(), node.getIcon(),
                    node.getPermissionId(), node.getSortOrder(), node.getIsVisible(), node.getKeepAlive(),
                    node.getStatus(), node.getCreateTime(), node.getUpdateTime(), node.getPermissionCode(),
                    build(node.getMenuId(), children)));
        }
        return result;
    }

    /**
     * 先检查权限，再确认菜单是否存在，禁用项也能查到。
     *
     * @param menu 数据库查询结果
     * @return 存在的菜单，不存在返回 404
     */
    private SysMenu requireMenu(SysMenu menu) {
        if (menu == null) {
            throw new BusinessException(404, "NOT_FOUND", "菜单不存在");
        }
        return menu;
    }

    /**
     * 拿到菜单锁后先查单个带引号版本，再查结构、状态和删除引用限制。
     *
     * @param menu 当前锁定节点
     * @param revision 客户端原样传递的详情版本
     */
    private void checkRevision(SysMenu menu, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(menu).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "菜单已修改，请重新获取");
        }
    }

    /**
     * 在同一快照或菜单锁事务里按固定顺序计算业务字段摘要，时间不算版本。
     *
     * @param menu 当前菜单
     * @return 对外详情与带引号 SHA-256 版本；子节点和角色关联不属于菜单可写模型
     */
    private MenuProfileVO profile(SysMenu menu) {
        MenuVO value = view(menu);
        String canonical = json.writeValueAsString(Arrays.asList(value.menuId(), value.parentId(), value.menuCode(),
                value.menuName(), value.menuType(), value.routeName(), value.path(), value.component(), value.icon(),
                value.permissionId(), value.sortOrder(), value.isVisible(), value.keepAlive(), value.status()));
        return new MenuProfileVO(value, "\"" + TokenUtils.digest(canonical) + "\"");
    }

    /**
     * 只把约定字段放进菜单响应，实体以后加字段也不能自动泄露出去。
     *
     * @param menu 一致快照或锁事务内已确认存在的菜单
     * @return 菜单资料和数据库维护的 UTC 审计时间
     */
    private MenuVO view(SysMenu menu) {
        return new MenuVO(menu.getMenuId(), menu.getParentId(), menu.getMenuCode(), menu.getMenuName(),
                menu.getMenuType(),
                menu.getRouteName(), menu.getPath(), menu.getComponent(), menu.getIcon(), menu.getPermissionId(),
                menu.getSortOrder(), menu.getIsVisible(), menu.getKeepAlive(), menu.getStatus(),
                menu.getCreateTime(), menu.getUpdateTime());
    }

    /** 在结构保护锁下验证整批叶子，关联清理与数据库审计共同提交。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "sys:menu", permission = "sys:menu:delete")
    public void deleteBatch(List<BatchItemDTO> items) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "菜单结构检查点未初始化");
        }
        requireAdmin("sys:menu:delete");
        var sorted = BatchInputs.sorted(items);
        List<Long> ids = sorted.stream().map(item -> IdUtils.parse(item.id())).toList();
        Set<Long> affected = new TreeSet<>(menus.userIds(ids));
        affected.add(sessions.currentUserId());
        for (long id : affected) {
            users.lockById(id);
        }
        Map<Long, SysMenu> nodes = new LinkedHashMap<>();
        menus.allMenus().forEach(node -> nodes.put(node.getMenuId(), node));
        for (var item : sorted) {
            long menuId = IdUtils.parse(item.id());
            SysMenu menu = menus.lockById(menuId);
            requireAdmin("sys:menu:delete");
            menu = requireMenu(menu);
            checkRevision(menu, item.revision());
            if (nodes.values().stream().anyMatch(node -> Long.valueOf(menuId).equals(node.getParentId()))) {
                throw new BusinessException(409, "RESOURCE_IN_USE", "菜单仍有子节点");
            }
        }
        for (long id : ids) {
            menus.deleteById(id);
        }
    }

}
