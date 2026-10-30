package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口权限单独保存，菜单存不存在、看不看得见都不能代替权限检查。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    /** 权限ID。 */
    @TableId(value = "permission_id", type = IdType.AUTO)
    private Long permissionId;

    /** 按“模块:资源:动作”命名，后端注解和权限检查使用，多个接口可以共用。 */
    @TableField("permission_code")
    private String permissionCode;

    /** 权限显示名称。 */
    @TableField("permission_name")
    private String permissionName;

    /** 所属业务模块，如admin、blog、sys。 */
    @TableField("module")
    private String module;

    /** 权限用途及使用说明。 */
    @TableField("description")
    private String description;

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
