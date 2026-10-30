package com.xuan.vo;

import java.time.LocalDateTime;

/** 后台菜单持久化业务字段；不混入本人访问过滤或子节点。 */
public record MenuVO(Long menuId, Long parentId, String menuCode, String menuName, String menuType,
                     String routeName, String path, String component, String icon, Long permissionId,
                     Integer sortOrder, Integer isVisible, Integer keepAlive, Integer status,
                     LocalDateTime createTime, LocalDateTime updateTime) { }
