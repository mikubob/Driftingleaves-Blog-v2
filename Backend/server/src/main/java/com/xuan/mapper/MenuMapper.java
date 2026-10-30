package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysMenu;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单写入先锁固定结构检查点，再锁用户和目标菜单，顺序由业务层统一控制。
 */
public interface MenuMapper extends BaseMapper<SysMenu> {

    /**
     * 取全部状态的菜单和权限码；后台读用同一快照，写入检查前要先锁住菜单结构。
     *
     * @return sortOrder、menuId 升序的节点，不按可见性或权限状态过滤
     */
    List<MenuGrant> allMenus();

    /**
     * 持有结构锁时找出目标分支关联的全部用户，禁用角色和用户也要算。
     *
     * @param menuIds 非空且去重的受影响菜单 ID
     * @return 去重升序的用户，由 Service 在节点锁前升序锁定
     */
    List<Long> userIds(@Param("menuIds") List<Long> menuIds);

    /**
     * 先拿用户锁再锁目标菜单，禁用菜单也能锁到。
     *
     * @param menuId 目标菜单
     * @return 当前菜单，不存在为 null；调用方要持有结构锁和写事务
     */
    SysMenu lockById(@Param("menuId") long menuId);

    /**
     * 完整替换可写字段，传 NULL 就清空，编码和数据库维护的时间不改。
     *
     * @param menu 已持有锁且通过版本、结构校验的菜单
     * @return 更新数；关联和独立权限保持，由调用方事务提交
     */
    int updateMenu(@Param("menu") SysMenu menu);
}
