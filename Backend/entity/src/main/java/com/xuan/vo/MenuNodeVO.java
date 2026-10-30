package com.xuan.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 菜单树节点；后台完整树保留全部状态，本人树经过有效授权与祖先链过滤，按钮不生成路由。 */
public record MenuNodeVO(Long menuId, Long parentId, String menuCode, String menuName, String menuType,
                         String routeName, String path, String component, String icon, Long permissionId,
                         Integer sortOrder, Integer isVisible, Integer keepAlive, Integer status,
                         LocalDateTime createTime, LocalDateTime updateTime, String permissionCode,
                         List<MenuNodeVO> children) { }
