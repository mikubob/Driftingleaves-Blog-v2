package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色可访问的菜单/按钮；配置时同时选择祖先节点；复合键为 role_id, menu_id，不提供单 ID CRUD。 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {
    /** 角色ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 菜单ID。 */
    @TableField("menu_id")
    private Long menuId;

    /** 角色获授该菜单的时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
