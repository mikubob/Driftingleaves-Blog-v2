package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 新建分类时只收这些字段，没填排序和状态就用默认值。 */
@Getter
public class CategoryCreateDTO {
    @NotNull @TextLength(min = 1, max = 64)
    private String categoryName;
    @NotNull @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") @TextLength(max = 100)
    private String slug;
    @TextLength(max = 255)
    private String description;
    @NotNull @Setter
    private Integer sortOrder = 0;
    @NotNull @Min(0) @Max(1) @Setter
    private Integer status = 1;

    public void setCategoryName(String value) { categoryName = value == null ? null : value.strip(); }
    public void setSlug(String value) { slug = value == null ? null : value.strip(); }
    public void setDescription(String value) {
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
