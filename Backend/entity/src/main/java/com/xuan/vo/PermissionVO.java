package com.xuan.vo;

import java.time.LocalDateTime;

/** 权限字典对外投影，禁用项仍可查询。 */
public record PermissionVO(Long permissionId, String permissionCode, String permissionName, String module,
                           String description, Integer status, LocalDateTime createTime, LocalDateTime updateTime) { }
