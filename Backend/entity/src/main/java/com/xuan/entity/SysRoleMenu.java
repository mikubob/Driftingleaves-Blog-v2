package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色能看到哪些菜单和按钮存这里，配置时要补齐祖先；role_id、menu_id 一起确定一条关联，不能用单个 ID 做增删改查。 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {
    /** 角色ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 菜单ID。 */
    @TableField("menu_id")
    private Long menuId;

    /** 给角色授予菜单的时间，由数据库填写。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
