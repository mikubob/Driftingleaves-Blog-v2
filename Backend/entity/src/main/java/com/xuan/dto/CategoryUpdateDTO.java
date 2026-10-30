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

/** 修改分类要把资料填全，description 可以清空，但不能漏传。 */
@Getter
public class CategoryUpdateDTO {
    @NotNull @TextLength(min = 1, max = 64)
    private String categoryName;
    @NotNull @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") @TextLength(max = 100)
    private String slug;
    @TextLength(max = 255)
    private String description;
    @NotNull @Setter
    private Integer sortOrder;
    @NotNull @Min(0) @Max(1) @Setter
    private Integer status;
    @Getter(AccessLevel.NONE)
    private boolean descriptionPresent;

    public void setCategoryName(String value) { categoryName = value == null ? null : value.strip(); }
    public void setSlug(String value) { slug = value == null ? null : value.strip(); }
    public void setDescription(String value) {
        descriptionPresent = true;
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }
    @AssertTrue(message = "description必须显式提供，可为null")
    public boolean isCompleteReplace() { return descriptionPresent; }
}
