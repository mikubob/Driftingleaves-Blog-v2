package com.xuan.vo;

/** 用户已分配的角色，只返回详情展示需要的名称、编码和状态。 */
public record UserRoleVO(Long roleId, String roleCode, String roleName, Integer status) {

}
