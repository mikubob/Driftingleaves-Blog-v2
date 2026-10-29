package com.xuan.vo;

/** 服务内部携带后台用户资料及对应聚合版本，版本仅放入响应头。 */
public record AdminUserProfileVO(UserAdminVO user, String revision) { }
