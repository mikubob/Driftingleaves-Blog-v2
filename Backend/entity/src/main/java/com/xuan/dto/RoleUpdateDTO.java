package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * 角色资料要填全，状态单独批量修改，编码不能改，可空的说明也要传。
 */
@Getter
public class RoleUpdateDTO {

    @NotNull
    @TextLength(min = 1, max = 64)
    private String roleName;

    @TextLength(max = 255)
    private String description;

    @NotNull
    @Pattern(regexp = "ALL|SELF", message = "请选择全部数据或本人数据")
    private String dataScope;

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

    @AssertTrue(message = "描述未完整提交，请重新打开编辑窗口后重试；描述留空可清除已有内容。")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
