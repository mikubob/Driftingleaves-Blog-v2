package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.entity.SysPermission;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 权限字典持久化；Service 写事务统一保护锁、升序用户锁及目标权限锁。 */
public interface PermissionMapper extends BaseMapper<SysPermission> {
    /**
     * 同条件查询总数和列表，字面包含编码或名称，模块和状态精确匹配。
     * @param page 分页参数
     * @param query 已校验查询条件
     * @return ID 升序的完整状态分页
     */
    IPage<SysPermission> permissionPage(IPage<SysPermission> page, @Param("query") PermissionQueryDTO query);

    /**
     * 固定保护锁内枚举全部关联用户，不按用户、角色或权限状态过滤。
     * @param permissionId 目标权限
     * @return 去重升序的用户 ID，由调用方随后锁定
     */
    List<Long> userIds(@Param("permissionId") long permissionId);

    /**
     * 用户锁后锁定目标权限，不过滤状态。
     * @param permissionId 权限 ID
     * @return 当前权限，不存在为 null；调用方须持有写事务
     */
    SysPermission lockById(@Param("permissionId") long permissionId);

    /**
     * 完整替换资料和状态，显式写 NULL，不修改编码及审计字段。
     * @param permission 已锁定且通过版本检查的实体
     * @return 更新数，由调用方事务协调状态和会话撤销
     */
    int updatePermission(@Param("permission") SysPermission permission);

    /**
     * 固定保护锁内检查任意状态菜单引用，不能静默删除门禁。
     * @param permissionId 已锁定目标权限
     * @return 引用菜单数，非零时禁止删除
     */
    long menuReferences(@Param("permissionId") long permissionId);
}
