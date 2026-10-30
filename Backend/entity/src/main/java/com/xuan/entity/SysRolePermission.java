package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口权限不能从菜单倒推；role_id、permission_id 一起确定一条授权，不能用单个 ID 做增删改查。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    /** 角色ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 权限ID。 */
    @TableField("permission_id")
    private Long permissionId;

    /** 给角色授予权限的时间，由数据库填写。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
