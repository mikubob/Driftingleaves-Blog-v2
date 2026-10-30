package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 返回权限字典资料，被禁用的权限仍然能查到。
 */
public record PermissionVO(Long permissionId, String permissionCode, String permissionName, String module,
                           String description, Integer status, LocalDateTime createTime, LocalDateTime updateTime) { }
