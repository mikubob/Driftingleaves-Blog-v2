package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改分类要把资料填全，description 可以清空但不能漏传，展示状态单独批量修改。
 */
@Getter
public class CategoryUpdateDTO {

    @NotNull
    @TextLength(min = 1, max = 64)
    private String categoryName;

    @NotNull
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*", message = "只能使用字母、数字及分隔词语的单个连字符，不能包含空格或连续连字符")
    @TextLength(max = 100)
    private String slug;

    @TextLength(max = 255)
    private String description;

    @NotNull
    @Setter
    private Integer sortOrder;

    @Getter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setCategoryName(String value) {
        categoryName = value == null ? null : value.strip();
    }

    public void setSlug(String value) {
        slug = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /** 检查是否提交了分类描述，显式传空可清除原值。 */
    @AssertTrue(message = "描述未完整提交，请重新打开编辑窗口后重试；描述留空可清除已有内容。")
    public boolean isCompleteReplace() {
        return descriptionPresent;
    }
}
