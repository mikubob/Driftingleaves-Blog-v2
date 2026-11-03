package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建角色默认启用、范围为 SELF，权限要另外配置。
 */
@Getter
public class RoleCreateDTO {

    @NotNull
    @Pattern(regexp = "[a-z][a-z0-9_]{0,63}", message = "须以小写字母开头，只能包含小写字母、数字或下划线，最多64位")
    private String roleCode;

    @NotNull
    @TextLength(min = 1, max = 64)
    private String roleName;

    @TextLength(max = 255)
    private String description;

    @NotNull
    @Pattern(regexp = "ALL|SELF", message = "请选择全部数据或本人数据")
    private String dataScope = "SELF";

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer status = 1;

    public void setRoleCode(String value) {
        roleCode = value == null ? null : value.strip();
    }

    public void setRoleName(String value) {
        roleName = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    public void setDataScope(String value) {
        dataScope = value == null ? null : value.strip();
    }
}
