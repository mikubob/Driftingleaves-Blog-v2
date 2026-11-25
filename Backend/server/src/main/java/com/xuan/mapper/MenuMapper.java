package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysMenu;
import com.xuan.mapper.AuthorizationMapper.MenuGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单数据访问接口
 */
@Mapper
public interface MenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询全部状态的菜单及关联权限编码，按排序值与菜单 ID 升序排列。
     *
     * @return 菜单列表
     */
    List<MenuGrant> allMenus();

    /**
     * 查询指定菜单分支关联的全部用户 ID（含禁用角色和用户），按 ID 升序去重。
     *
     * @param menuIds 受影响的菜单 ID 列表
     * @return 用户 ID 列表
     */
    List<Long> userIds(@Param("menuIds") List<Long> menuIds);

    /**
     * 加行锁查询菜单记录，调用方须处于写事务内。
     *
     * @param menuId 菜单 ID
     * @return 锁定的菜单记录，不存在时返回 null
     */
    SysMenu lockById(@Param("menuId") long menuId);

    /**
     * 完整更新菜单可写字段，显式写入可空字段以支持清空。
     *
     * @param menu 待更新的菜单实体
     * @return 受影响行数
     */
    int updateMenu(@Param("menu") SysMenu menu);

    /**
     * 更新菜单状态
     *
     * @param id 菜单 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
