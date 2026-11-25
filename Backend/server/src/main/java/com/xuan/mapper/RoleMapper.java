package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.entity.SysMenu;
import com.xuan.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色数据访问接口
 */
@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {

    /**
     * 分页查询角色列表，按角色 ID 升序排列。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 角色分页结果
     */
    IPage<SysRole> rolePage(IPage<SysRole> page, @Param("query") RoleQueryDTO query);

    /**
     * 查询角色关联的全部权限 ID（含禁用权限），按 ID 升序排列。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<Long> permissionIds(@Param("roleId") long roleId);

    /**
     * 查询角色关联的全部菜单 ID（含禁用菜单），按 ID 升序排列。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    List<Long> menuIds(@Param("roleId") long roleId);

    /**
     * 查询角色关联的全部用户 ID（含禁用用户），按 ID 升序去重。
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表
     */
    List<Long> userIds(@Param("roleId") long roleId);

    /**
     * 加行锁查询角色记录，调用方须处于写事务内。
     *
     * @param roleId 角色 ID
     * @return 锁定的角色记录，不存在时返回 null
     */
    SysRole lockById(@Param("roleId") long roleId);

    /**
     * 完整更新角色资料、数据范围和状态，显式写入可空字段以支持清空。
     *
     * @param role 待更新的角色实体
     * @return 受影响行数
     */
    int updateRole(@Param("role") SysRole role);

    /**
     * 按 ID 升序加共享锁查询启用状态的权限 ID 列表
     *
     * @param ids 候选权限 ID 列表
     * @return 启用状态的权限 ID 列表
     */
    List<Long> lockEnabledPermissions(@Param("ids") List<Long> ids);

    /**
     * 查询全部菜单节点（含禁用项），用于校验菜单树与补齐祖先。
     *
     * @return 菜单列表
     */
    List<SysMenu> allMenus();

    /**
     * 清空指定角色的全部权限关联
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    int deletePermissions(@Param("roleId") long roleId);

    /**
     * 清空指定角色的全部菜单关联
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    int deleteMenus(@Param("roleId") long roleId);

    /**
     * 批量插入角色与权限的复合主键关联
     *
     * @param roleId 角色 ID
     * @param ids 权限 ID 列表
     * @return 插入行数
     */
    int insertPermissions(@Param("roleId") long roleId, @Param("ids") List<Long> ids);

    /**
     * 批量插入角色与菜单的复合主键关联
     *
     * @param roleId 角色 ID
     * @param ids 菜单 ID 列表
     * @return 插入行数
     */
    int insertMenus(@Param("roleId") long roleId, @Param("ids") List<Long> ids);

    /**
     * 更新角色状态
     *
     * @param id 角色 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
