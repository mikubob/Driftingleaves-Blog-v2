package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 后台目录/页面/按钮树；树环和跨层级规则由应用写入时校验。 */
@Data
@TableName("sys_menu")
public class SysMenu {
    /** 菜单ID。 */
    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long menuId;

    /** 根节点NULL；父节点必须为目录或页面。 */
    @TableField("parent_id")
    private Long parentId;

    /** 唯一菜单编码，供前端及授权配置识别。 */
    @TableField("menu_code")
    private String menuCode;

    /** 菜单显示名称。 */
    @TableField("menu_name")
    private String menuName;

    /** D目录 M页面 B按钮。 */
    @TableField("menu_type")
    private String menuType;

    /** Vue路由名，目录/页面唯一。 */
    @TableField("route_name")
    private String routeName;

    /** 前端路由，非后端接口URL。 */
    @TableField("path")
    private String path;

    /** 前端组件注册键；仅允许前端白名单组件。 */
    @TableField("component")
    private String component;

    /** 菜单图标标识，由前端图标库解析。 */
    @TableField("icon")
    private String icon;

    /** 页面访问/按钮所需权限；目录可为空。 */
    @TableField("permission_id")
    private Long permissionId;

    /** 排序值，数值越小越靠前。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 0不在导航显示 1显示；隐藏不等于撤权。 */
    @TableField("is_visible")
    private Integer isVisible;

    /** 是否缓存页面组件：0否 1是。 */
    @TableField("keep_alive")
    private Integer keepAlive;

    /** 0禁用 1启用；禁用目录时不返回整棵子树。 */
    @TableField("status")
    private Integer status;

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
