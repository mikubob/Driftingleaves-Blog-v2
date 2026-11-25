package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户已分配的角色，只返回详情展示需要的名称、编码和状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 角色ID
    private Long roleId;

    // 角色编码
    private String roleCode;

    // 角色名称
    private String roleName;

    // 状态
    private Integer status;
}
