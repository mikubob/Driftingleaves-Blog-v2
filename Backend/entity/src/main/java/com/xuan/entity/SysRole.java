package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色没有权重继承，谁有哪项权限要明确授权。 */
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

    /** ALL全部、SELF本人；只合并授予当前这项权限的角色范围。 */
    @TableField("data_scope")
    private String dataScope;

    /** 0禁用 1启用。 */
    @TableField("status")
    private Integer status;

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
