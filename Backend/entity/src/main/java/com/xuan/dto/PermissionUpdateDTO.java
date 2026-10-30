package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 修改权限要填全，编码不能改，可空的说明也要传。 */
@Getter
public class PermissionUpdateDTO {
    @NotNull @TextLength(min = 1, max = 64)
    private String permissionName;
    @NotNull @Pattern(regexp = "[a-z][a-z0-9_]{0,31}")
    private String module;
    @TextLength(max = 255)
    private String description;
    @NotNull @Min(0) @Max(1) @Setter
    private Integer status;
    @Getter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setPermissionName(String value) { permissionName = value == null ? null : value.strip(); }
    public void setModule(String value) { module = value == null ? null : value.strip(); }
    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }
    @AssertTrue(message = "description必须显式提供，可为null")
    public boolean isCompleteReplace() { return descriptionPresent; }
}
