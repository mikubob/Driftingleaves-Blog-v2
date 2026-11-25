package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改权限要填全，编码不能改，可空的说明也要传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    // 是否传入描述
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setPermissionName(String value) {
        permissionName = value == null ? null : value.strip();
    }

    public void setModule(String value) {
        module = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /**
     * 检查是否提交了权限描述，显式传空可清除原值。
     */
    @AssertTrue(message = "描述未完整提交，请重新打开编辑窗口后重试；描述留空可清除已有内容。")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
