package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色；角色之间不按权重隐式继承权限。 */
@Data
@TableName("sys_role")
public class SysRole {
    /** 角色ID。 */
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    /** 稳定角色码，不带ROLE_前缀。 */
    @TableField("role_code")
    private String roleCode;

    /** 角色显示名称。 */
    @TableField("role_name")
    private String roleName;

    /** 角色说明。 */
    @TableField("description")
    private String description;

    /** ALL全部 SELF本人；按授予当前权限的角色计算。 */
    @TableField("data_scope")
    private String dataScope;

    /** 0禁用 1启用。 */
    @TableField("status")
    private Integer status;

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
