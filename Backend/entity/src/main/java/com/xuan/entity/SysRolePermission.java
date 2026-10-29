package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色接口权限；不从角色菜单表推导；复合键为 role_id, permission_id，不提供单 ID CRUD。 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    /** 角色ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 权限ID。 */
    @TableField("permission_id")
    private Long permissionId;

    /** 角色获授该权限的时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
