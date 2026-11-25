package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限数据访问接口
 */
@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 分页查询权限列表，按权限 ID 升序排列。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 权限分页结果
     */
    IPage<SysPermission> permissionPage(IPage<SysPermission> page, @Param("query") PermissionQueryDTO query);

    /**
     * 查询指定权限关联的全部用户 ID（含禁用角色和用户），按 ID 升序去重。
     *
     * @param permissionId 权限 ID
     * @return 用户 ID 列表
     */
    List<Long> userIds(@Param("permissionId") long permissionId);

    /**
     * 加行锁查询权限记录，调用方须处于写事务内。
     *
     * @param permissionId 权限 ID
     * @return 锁定的权限记录，不存在时返回 null
     */
    SysPermission lockById(@Param("permissionId") long permissionId);

    /**
     * 完整更新权限资料和状态，显式写入可空字段以支持清空。
     *
     * @param permission 待更新的权限实体
     * @return 受影响行数
     */
    int updatePermission(@Param("permission") SysPermission permission);

    /**
     * 统计引用指定权限的菜单数量（含禁用菜单）
     *
     * @param permissionId 权限 ID
     * @return 引用数量
     */
    long menuReferences(@Param("permissionId") long permissionId);

    /**
     * 更新权限状态
     *
     * @param id 权限 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
