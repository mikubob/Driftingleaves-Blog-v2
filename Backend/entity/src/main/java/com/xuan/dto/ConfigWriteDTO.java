package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 配置完整替换，内部对象由配置键的固定字段规则校验，不开放任意系统参数。
 */
@Getter
public class ConfigWriteDTO {

    @NotNull
    @Setter
    private Map<String, Object> configValue;

    @TextLength(max = 255)
    private String description;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer isPublic;

    @Getter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /** description 可清空，但不能漏传。 */
    @AssertTrue(message = "描述未完整提交，请重新打开编辑窗口后重试；描述留空可清除已有内容。")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
