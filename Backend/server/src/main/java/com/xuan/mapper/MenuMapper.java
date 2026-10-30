package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysMenu;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 菜单结构持久化；写入由 Service 统一先锁固定结构检查点，再锁用户及目标节点。 */
public interface MenuMapper extends BaseMapper<SysMenu> {
    /**
     * 查询所有状态节点及字典权限码；后台读取用一致快照，写入校验须先持有结构锁。
     * @return sortOrder、menuId 升序的节点，不按可见性或权限状态过滤
     */
    List<MenuGrant> allMenus();

    /**
     * 结构锁内枚举目标分支菜单关联的全部用户，不过滤角色或用户状态。
     * @param menuIds 非空且去重的受影响菜单 ID
     * @return 去重升序的用户，由 Service 在节点锁前升序锁定
     */
    List<Long> userIds(@Param("menuIds") List<Long> menuIds);

    /**
     * 用户锁后锁定目标节点，不过滤禁用状态。
     * @param menuId 目标菜单
     * @return 当前菜单，不存在为 null；调用方须持有结构锁和写事务
     */
    SysMenu lockById(@Param("menuId") long menuId);

    /**
     * 完整替换可写字段，显式写 NULL，保留编码和数据库审计字段。
     * @param menu 已持有锁且通过版本、结构校验的菜单
     * @return 更新数；关联和独立权限保持，由调用方事务提交
     */
    int updateMenu(@Param("menu") SysMenu menu);
}
