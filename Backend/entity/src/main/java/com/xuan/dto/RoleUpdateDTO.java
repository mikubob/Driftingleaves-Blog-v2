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
 * 角色资料要填全，状态单独批量修改，编码不能改，可空的说明也要传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 角色名称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String roleName;

    // 描述
    @TextLength(max = 255)
    private String description;

    // 数据范围 ALL-全部 SELF-本人
    @NotNull
    @Pattern(regexp = "ALL|SELF", message = "请选择全部数据或本人数据")
    private String dataScope;

    // 是否传入描述
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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

    /**
     * 检查是否提交了角色描述，显式传空可清除原值。
     */
    @AssertTrue(message = "描述未完整提交，请重新打开编辑窗口后重试；描述留空可清除已有内容。")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
