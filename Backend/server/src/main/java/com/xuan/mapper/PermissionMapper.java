package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.entity.SysPermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限字典写入依次锁保护点、升序用户和目标权限，顺序由业务层负责。
 */
public interface PermissionMapper extends BaseMapper<SysPermission> {
    /**
     * 列表和总数共用条件，编码、名称按文字包含查找，模块和状态精确匹配。
     * @param page 分页参数
     * @param query 已校验的查询条件
     * @return ID 升序的完整状态分页
     */
    IPage<SysPermission> permissionPage(IPage<SysPermission> page, @Param("query") PermissionQueryDTO query);

    /**
     * 持有保护锁时找全部关联用户，不过滤用户、角色或权限状态。
     * @param permissionId 目标权限
     * @return 去重升序的用户 ID，由调用方随后锁定
     */
    List<Long> userIds(@Param("permissionId") long permissionId);

    /**
     * 先拿用户锁再锁目标权限，不按状态过滤。
     * @param permissionId 权限 ID
     * @return 当前权限，不存在为 null；调用方要持有写事务
     */
    SysPermission lockById(@Param("permissionId") long permissionId);

    /**
     * 完整替换资料和状态，NULL 也要写，编码和时间字段不改。
     * @param permission 已锁定且通过版本检查的实体
     * @return 更新数，由调用方事务处理状态变化和会话撤销
     */
    int updatePermission(@Param("permission") SysPermission permission);

    /**
     * 持有保护锁时检查全部菜单引用，无论菜单状态如何都不能悄悄删掉权限门禁。
     * @param permissionId 已锁定目标权限
     * @return 引用菜单数，非零时禁止删除
     */
    long menuReferences(@Param("permissionId") long permissionId);
}
