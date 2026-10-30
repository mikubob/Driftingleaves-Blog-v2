package com.xuan.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回角色资料及全部授权，禁用的关联也要保留，ID 从小到大排序。
 */
public record RoleVO(Long roleId, String roleCode, String roleName, String description, String dataScope,
                     Integer status, LocalDateTime createTime, LocalDateTime updateTime,
                     List<Long> permissionIds, List<Long> menuIds) {

}
