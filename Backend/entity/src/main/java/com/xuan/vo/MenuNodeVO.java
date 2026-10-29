package com.xuan.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 有效授权和祖先链过滤后的菜单，隐藏项仍保留，按钮不生成路由。 */
public record MenuNodeVO(Long menuId, Long parentId, String menuCode, String menuName, String menuType,
                         String routeName, String path, String component, String icon, Long permissionId,
                         Integer sortOrder, Integer isVisible, Integer keepAlive, Integer status,
                         LocalDateTime createTime, LocalDateTime updateTime, String permissionCode,
                         List<MenuNodeVO> children) { }
