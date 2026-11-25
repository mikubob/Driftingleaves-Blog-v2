package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单树数据，后台保留全部状态，本人菜单仅保留已授权且全部上级有效的节点，按钮不生成路由。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuNodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 菜单ID
    private Long menuId;

    // 父级ID
    private Long parentId;

    // 菜单编码
    private String menuCode;

    // 菜单名称
    private String menuName;

    // 菜单类型
    private String menuType;

    // 路由名称
    private String routeName;

    // 路由路径
    private String path;

    // 前端组件
    private String component;

    // 图标
    private String icon;

    // 权限ID
    private Long permissionId;

    // 排序权重
    private Integer sortOrder;

    // 是否显示 0-隐藏 1-显示
    private Integer isVisible;

    // 是否缓存页面 0-否 1-是
    private Integer keepAlive;

    // 状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // 权限编码
    private String permissionCode;

    // 子级列表
    private List<MenuNodeVO> children;
}
