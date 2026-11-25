package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建权限默认启用，不会自动授予任何角色。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 权限编码
    @NotNull
    @TextLength(min = 1, max = 100)
    @Pattern(regexp = "admin:access|[a-z][a-z0-9]*:[a-z][A-Za-z0-9]*:[a-z][A-Za-z0-9]*", message = "须使用模块:资源:操作格式，例如blog:article:query")
    private String permissionCode;

    // 权限名称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String permissionName;

    // 所属模块
    @NotNull
    @Pattern(regexp = "[a-z][a-z0-9_]{0,31}", message = "须以小写字母开头，只能包含小写字母、数字或下划线，最多32位")
    private String module;

    // 描述
    @TextLength(max = 255)
    private String description;

    // 状态
    @NotNull
    @Min(0)
    @Max(1)
    @Builder.Default
    private Integer status = 1;

    public void setPermissionCode(String value) {
        permissionCode = value == null ? null : value.strip();
    }

    public void setPermissionName(String value) {
        permissionName = value == null ? null : value.strip();
    }

    public void setModule(String value) {
        module = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
