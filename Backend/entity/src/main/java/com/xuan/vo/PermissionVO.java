package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 返回权限字典资料，被禁用的权限仍然能查到。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 权限ID
    private Long permissionId;

    // 权限编码
    private String permissionCode;

    // 权限名称
    private String permissionName;

    // 所属模块
    private String module;

    // 描述
    private String description;

    // 状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
