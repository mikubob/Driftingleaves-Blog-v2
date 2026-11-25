package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 配置完整替换，内部对象由配置键的固定字段规则校验，不开放任意系统参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 配置值
    @NotNull
    private Map<String, Object> configValue;

    // 描述
    @TextLength(max = 255)
    private String description;

    // 是否公开 0-否 1-是
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isPublic;

    // 是否传入描述
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /**
     * description 可清空，但不能漏传。
     */
    @AssertTrue(message = "描述未完整提交，请重新打开编辑窗口后重试；描述留空可清除已有内容。")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
