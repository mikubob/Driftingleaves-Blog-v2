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
    @AssertTrue(message = "description必须显式提供，可为null")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
