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

/**
 * 角色资料和状态要填全，编码不能改，可空的说明也要传。
 */
@Getter
public class RoleUpdateDTO {

    @NotNull
    @TextLength(min = 1, max = 64)
    private String roleName;

    @TextLength(max = 255)
    private String description;

    @NotNull
    @Pattern(regexp = "ALL|SELF")
    private String dataScope;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer status;

    @Getter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setRoleName(String value) {
        roleName = value == null ? null : value.strip();
    }

    public void setDataScope(String value) {
        dataScope = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    @AssertTrue(message = "description必须显式提供，可为null")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
