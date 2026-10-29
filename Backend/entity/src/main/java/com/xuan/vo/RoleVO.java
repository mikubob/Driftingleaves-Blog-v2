package com.xuan.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 角色资料及完整授权集合；包含禁用关联，ID 按升序返回。 */
public record RoleVO(Long roleId, String roleCode, String roleName, String description, String dataScope,
                     Integer status, LocalDateTime createTime, LocalDateTime updateTime,
                     List<Long> permissionIds, List<Long> menuIds) { }
