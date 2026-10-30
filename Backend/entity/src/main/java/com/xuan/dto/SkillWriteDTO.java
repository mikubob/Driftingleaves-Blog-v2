package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * 创建和PUT均须显式提供全部字段，可空字段用null清空。
 */
@Getter
public class SkillWriteDTO {

    @NotNull
    @TextLength(min = 1, max = 64)
    private String name;

    @TextLength(min = 1, max = 500)
    private String description;

    @TextLength(min = 1, max = 2048)
    private String icon;

    @NotNull
    private Integer sortOrder;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;

    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setName(String value) {
        present.add("name");
        name = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        present.add("description");
        description = value == null ? null : value.strip();
    }

    public void setIcon(String value) {
        present.add("icon");
        icon = value == null ? null : value.strip();
    }

    public void setSortOrder(Integer value) {
        present.add("sortOrder");
        sortOrder = value;
    }

    public void setStatus(Integer value) {
        present.add("status");
        status = value;
    }

    /** 创建和替换都必须提供可空字段，防止漏传被当作清空。 */
    @AssertTrue(message = "必须提供全部可写字段，可空字段可为null")
    public boolean isComplete() {
        return present.size() == 5;
    }
}
