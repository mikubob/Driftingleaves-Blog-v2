package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 接口/操作权限字典；不依赖前端菜单是否存在或可见。 */
@Data
@TableName("sys_permission")
public class SysPermission {
    /** 权限ID。 */
    @TableId(value = "permission_id", type = IdType.AUTO)
    private Long permissionId;

    /** 模块:资源:动作；后端注解/鉴权使用，多个接口可复用。 */
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

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
