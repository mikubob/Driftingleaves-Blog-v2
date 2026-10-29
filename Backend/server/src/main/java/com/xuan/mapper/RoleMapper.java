package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.entity.SysMenu;
import com.xuan.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 角色及授权集合持久化；写入由 Service 统一持有保护锁、用户锁和角色锁。 */
public interface RoleMapper extends BaseMapper<SysRole> {
    /**
     * 按编码、名称字面包含及状态过滤，固定 ID 升序，计数复用相同条件。
     * @param page 分页请求
     * @param query 已校验过滤条件
     * @return 包含禁用角色的内部实体分页
     */
    IPage<SysRole> rolePage(IPage<SysRole> page, @Param("query") RoleQueryDTO query);

    /**
     * 查询完整权限关联，不按权限状态过滤。
     * @param roleId 角色 ID
     * @return 权限 ID 升序集合
     */
    List<Long> permissionIds(@Param("roleId") long roleId);

    /**
     * 查询完整菜单关联，不按菜单状态过滤。
     * @param roleId 角色 ID
     * @return 菜单 ID 升序集合
     */
    List<Long> menuIds(@Param("roleId") long roleId);

    /**
     * 保护锁内枚举需撤销会话的全部关联用户，包含禁用用户。
     * @param roleId 目标角色 ID
     * @return 用户 ID 升序集合；调用方随后按序锁定用户
     */
    List<Long> userIds(@Param("roleId") long roleId);

    /**
     * 用户锁后锁定目标角色，不提前过滤状态。
     * @param roleId 角色 ID
     * @return 当前角色；不存在为 null
     */
    SysRole lockById(@Param("roleId") long roleId);

    /**
     * 显式替换角色资料、范围和状态，可空说明写入 NULL。
     * @param role 已锁定并通过版本检查的角色，不修改编码与审计字段
     * @return 影响行数
     */
    int updateRole(@Param("role") SysRole role);

    /**
     * 按 ID 升序锁定本次权限，供存在性及启用校验。
     * @param ids 非空排序权限集合
     * @return 当前启用权限 ID；数量不足时调用方拒绝整笔授权
     */
    List<Long> lockEnabledPermissions(@Param("ids") List<Long> ids);

    /**
     * 在固定结构保护锁内读取菜单树，供祖先补全和门禁检查。
     * @return 全部菜单节点，包含禁用项
     */
    List<SysMenu> allMenus();

    /**
     * 完整替换前删除角色权限，调用方事务保证与菜单共同提交。
     * @param roleId 已锁定角色
     * @return 删除数
     */
    int deletePermissions(@Param("roleId") long roleId);

    /**
     * 完整替换前删除角色菜单，调用方事务保证与权限共同提交。
     * @param roleId 已锁定角色
     * @return 删除数
     */
    int deleteMenus(@Param("roleId") long roleId);

    /**
     * 插入已校验权限复合键集合；空集合由调用方跳过。
     * @param roleId 已锁定角色
     * @param ids 非空有效权限集合
     * @return 插入数
     */
    int insertPermissions(@Param("roleId") long roleId, @Param("ids") List<Long> ids);

    /**
     * 插入已补齐祖先并校验门禁的菜单集合；空集合由调用方跳过。
     * @param roleId 已锁定角色
     * @param ids 非空菜单集合
     * @return 插入数
     */
    int insertMenus(@Param("roleId") long roleId, @Param("ids") List<Long> ids);
}
