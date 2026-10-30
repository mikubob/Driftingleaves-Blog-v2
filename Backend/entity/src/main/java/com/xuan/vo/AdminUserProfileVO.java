package com.xuan.vo;

/**
 * 内部一起传递后台用户资料和版本，版本只放响应头。
 */
public record AdminUserProfileVO(UserAdminVO user, String revision) {

}
