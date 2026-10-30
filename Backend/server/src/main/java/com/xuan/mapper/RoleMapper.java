package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.entity.SysMenu;
import com.xuan.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 角色和授权一起保存，写入时业务层先后锁保护点、用户和角色。 */
public interface RoleMapper extends BaseMapper<SysRole> {
    /**
     * 编码和名称按文字包含查找，状态精确匹配，按 ID 升序分页，总数用同样条件。
     * @param page 分页请求
     * @param query 已经检查过的过滤条件
     * @return 包含禁用角色的内部实体分页
     */
    IPage<SysRole> rolePage(IPage<SysRole> page, @Param("query") RoleQueryDTO query);

    /**
     * 查全部权限关联，禁用权限也保留。
     * @param roleId 角色 ID
     * @return 权限 ID 升序集合
     */
    List<Long> permissionIds(@Param("roleId") long roleId);

    /**
     * 查全部菜单关联，禁用菜单也保留。
     * @param roleId 角色 ID
     * @return 菜单 ID 升序集合
     */
    List<Long> menuIds(@Param("roleId") long roleId);

    /**
     * 持有保护锁时找出要退出登录的全部关联用户，禁用用户也要包括。
     * @param roleId 目标角色 ID
     * @return 用户 ID 升序集合；调用方随后按序锁定用户
     */
    List<Long> userIds(@Param("roleId") long roleId);

    /**
     * 先拿用户锁再锁角色，不能先把禁用角色过滤掉。
     * @param roleId 角色 ID
     * @return 当前角色；不存在为 null
     */
    SysRole lockById(@Param("roleId") long roleId);

    /**
     * 替换角色资料、范围和状态，说明为 NULL 时也要清空。
     * @param role 已锁定并通过版本检查的角色，不修改编码与审计字段
     * @return 影响行数
     */
    int updateRole(@Param("role") SysRole role);

    /**
     * 按 ID 从小到大锁定本次权限，再查是否存在、是否启用。
     * @param ids 非空排序权限集合
     * @return 当前启用权限 ID；数量不足时调用方拒绝整笔授权
     */
    List<Long> lockEnabledPermissions(@Param("ids") List<Long> ids);

    /**
     * 持有固定结构锁时取菜单树，用来补齐祖先并检查访问权限。
     * @return 全部菜单节点，包含禁用项
     */
    List<SysMenu> allMenus();

    /**
     * 整体替换前先清掉角色权限，调用方事务保证它和菜单一起提交。
     * @param roleId 已锁定角色
     * @return 删除数
     */
    int deletePermissions(@Param("roleId") long roleId);

    /**
     * 整体替换前先清掉角色菜单，调用方事务保证它和权限一起提交。
     * @param roleId 已锁定角色
     * @return 删除数
     */
    int deleteMenus(@Param("roleId") long roleId);

    /**
     * 插入已经检查过的角色权限两列主键，空集合由调用方直接跳过。
     * @param roleId 已锁定角色
     * @param ids 非空有效权限集合
     * @return 插入数
     */
    int insertPermissions(@Param("roleId") long roleId, @Param("ids") List<Long> ids);

    /**
     * 插入已补齐祖先、查过访问权限的菜单集合，空集合由调用方跳过。
     * @param roleId 已锁定角色
     * @param ids 非空菜单集合
     * @return 插入数
     */
    int insertMenus(@Param("roleId") long roleId, @Param("ids") List<Long> ids);
}
