package com.xuan.vo;

/** 服务内部角色聚合结果，版本只写入响应头。 */
public record RoleProfileVO(RoleVO role, String revision) { }
