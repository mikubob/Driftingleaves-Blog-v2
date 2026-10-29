package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户多角色；复合主键防重复授权；复合键为 user_id, role_id，不提供单 ID CRUD。 */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    /** 用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 角色ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 用户获授该角色的时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
