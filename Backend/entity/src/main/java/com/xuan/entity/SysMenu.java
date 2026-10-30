package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单分目录、页面、按钮，写入时要检查父子类型和循环引用。
 */
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

    /** 前端组件注册键，只能选前端允许的组件。 */
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

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
