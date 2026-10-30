package com.xuan.vo;

import java.time.LocalDateTime;

/** 这里只返回后台菜单本身的字段，不带子节点，也不按本人访问条件筛选。 */
public record MenuVO(Long menuId, Long parentId, String menuCode, String menuName, String menuType,
                     String routeName, String path, String component, String icon, Long permissionId,
                     Integer sortOrder, Integer isVisible, Integer keepAlive, Integer status,
                     LocalDateTime createTime, LocalDateTime updateTime) { }
