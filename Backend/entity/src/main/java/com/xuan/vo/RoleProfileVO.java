package com.xuan.vo;

/**
 * 内部一起传递角色资料和版本，版本只写到响应头。
 */
public record RoleProfileVO(RoleVO role, String revision) {

}
