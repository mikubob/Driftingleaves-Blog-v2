package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回角色资料及全部授权，禁用的关联也要保留，ID 从小到大排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 角色ID
    private Long roleId;

    // 角色编码
    private String roleCode;

    // 角色名称
    private String roleName;

    // 描述
    private String description;

    // 数据范围
    private String dataScope;

    // 状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // 权限ID列表
    private List<Long> permissionIds;

    // 菜单ID列表
    private List<Long> menuIds;
}
