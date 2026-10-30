package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 新建权限默认启用，不会自动授予任何角色。 */
@Getter
public class PermissionCreateDTO {
    @NotNull @TextLength(min = 1, max = 100)
    @Pattern(regexp = "admin:access|[a-z][a-z0-9]*:[a-z][A-Za-z0-9]*:[a-z][A-Za-z0-9]*")
    private String permissionCode;
    @NotNull @TextLength(min = 1, max = 64)
    private String permissionName;
    @NotNull @Pattern(regexp = "[a-z][a-z0-9_]{0,31}")
    private String module;
    @TextLength(max = 255)
    private String description;
    @NotNull @Min(0) @Max(1) @Setter
    private Integer status = 1;

    public void setPermissionCode(String value) { permissionCode = value == null ? null : value.strip(); }
    public void setPermissionName(String value) { permissionName = value == null ? null : value.strip(); }
    public void setModule(String value) { module = value == null ? null : value.strip(); }
    public void setDescription(String value) {
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
