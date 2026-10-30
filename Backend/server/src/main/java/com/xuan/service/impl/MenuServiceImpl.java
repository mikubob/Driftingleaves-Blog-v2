package com.xuan.service.impl;

import com.xuan.utils.TokenUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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

/** 菜单结构事务；固定检查点串行结构与授权写入，菜单实时读库且不撤销独立接口授权或会话。 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, SysMenu> implements IMenuService {
    /** 与当前 SQL 种子的前端注册约定一致；键存在不表示前端页面或后续业务已经实现。 */
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
     * 一致快照内验证双权限后查询完整后台树，不套用本人授权过滤。
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
     * 同一只读快照鉴权后读取资料和业务版本，禁用项仍可管理。
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
     * 保护锁和操作者锁内复核身份，验证白名单、祖先及门禁后创建，不建立角色关联。
     * @param request 已经格式校验和默认值处理的创建字段
     * @return 回查数据库审计时间的菜单；校验或唯一冲突整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
     * 同一结构锁事务完整替换字段，复核原 Token、双权限和版本，并检查全部受影响后代。
     * @param menuId 目标菜单
     * @param request 完整可写白名单，编码和审计字段不可输入
     * @param revision 单个带引号版本；缺失 428、格式错误 400、过期 412
     * @return 新详情及版本；不补角色祖先或新门禁权限，不撤销会话，失败整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MenuProfileVO update(long menuId, MenuUpdateDTO request, String revision) {
        // 1. 固定结构锁、升序用户锁之后锁目标，基于最新提交的树检查版本与结构。
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
        // 2. 将候选节点放回整树校验，自引用、移到后代和类型修改均不能破坏分支。
        validateFields(menu);
        nodes.put(menuId, menu);
        for (long id : descendants(menuId, nodes)) {
            validateChain(nodes.get(id), nodes);
        }
        // 3. 保留所有角色关联；下一次本人菜单读取按新结构和门禁实时计算。
        menus.updateMenu(menu);
        return profile(menus.selectById(menuId));
    }

    /**
     * 结构锁事务中先鉴权、锁用户及节点、检查版本，再删除没有子节点的菜单。
     * @param menuId 目标菜单
     * @param revision 当前详情版本，先于资源引用检查
     * @throws BusinessException 子节点存在返回 RESOURCE_IN_USE；叶子仅级联 role_menu，失败回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long menuId, String revision) {
        Map<Long, SysMenu> nodes = lockStructure(menuId);
        SysMenu menu = menus.lockById(menuId);
        requireAdmin("sys:menu:delete");
        menu = requireMenu(menu);
        checkRevision(menu, revision);
        if (nodes.values().stream().anyMatch(node -> Long.valueOf(menuId).equals(node.getParentId()))) {
            throw new BusinessException(409, "RESOURCE_IN_USE", "菜单仍有子节点");
        }
        menus.deleteById(menuId);
    }

    /**
     * 实时验证启用身份、原 Token 及后台入口和具体菜单权限；SELF 不缩小全局树。
     * @param permission 当前 sys:menu 操作权限
     */
    private void requireAdmin(String permission) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, permission);
    }

    /**
     * 调用方写事务先锁固定检查点，再升序锁操作者及受影响分支关联用户，节点锁后由入口复核身份权限。
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
     * 在结构锁内枚举目标及所有后代，用于影响用户锁和类型变更校验，访问集合避免环导致死循环。
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
        while (!pending.isEmpty()) {
            long id = pending.remove();
            if (found.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return List.copyOf(found);
    }

    /**
     * 校验输入字段组合、前端注册白名单与门禁存在性；禁用权限仍允许保留为门禁。
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
     * 验证 ASCII 内部路由及解码后路径，拒绝协议、父目录、查询、片段、反斜线和控制字符。
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
     * 结构锁内检查候选节点的完整祖先链和父类型，启停不改变合法结构且不强制父节点启用。
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
     * 将已按排序值和 ID 排好的完整节点逐层构树，调用方持有一致读取快照。
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
     * 鉴权之后确认菜单存在，不过滤状态。
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
     * 持有节点锁时检查单个带引号业务版本，先于结构、状态或删除引用限制。
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
     * 同一快照或节点锁事务生成所有持久化业务字段的固定顺序摘要，不包含审计时间。
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
     * 显式投影菜单响应字段，避免持久化实体扩展导致字段泄露。
     * @param menu 一致快照或锁事务内已确认存在的菜单
     * @return 菜单资料和数据库维护的 UTC 审计时间
     */
    private MenuVO view(SysMenu menu) {
        return new MenuVO(menu.getMenuId(), menu.getParentId(), menu.getMenuCode(), menu.getMenuName(), menu.getMenuType(),
                menu.getRouteName(), menu.getPath(), menu.getComponent(), menu.getIcon(), menu.getPermissionId(),
                menu.getSortOrder(), menu.getIsVisible(), menu.getKeepAlive(), menu.getStatus(),
                menu.getCreateTime(), menu.getUpdateTime());
    }
}
