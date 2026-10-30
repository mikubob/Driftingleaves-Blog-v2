package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户可以有多个角色，user_id、role_id 一起确定一条关联并防重复，不能用单个 ID 做增删改查。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    /** 用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 角色ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 给用户授予角色的时间，由数据库填写。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
