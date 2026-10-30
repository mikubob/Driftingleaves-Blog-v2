package com.xuan.vo;

/** Service 内部权限详情和版本，版本仅写入响应头。 */
public record PermissionProfileVO(PermissionVO permission, String revision) { }
