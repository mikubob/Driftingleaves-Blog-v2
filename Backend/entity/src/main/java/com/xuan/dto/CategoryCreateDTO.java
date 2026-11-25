package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建分类时只收这些字段，没填排序和状态就用默认值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 分类名称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String categoryName;

    // URL别名
    @NotNull
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*", message = "只能使用字母、数字及分隔词语的单个连字符，不能包含空格或连续连字符")
    @TextLength(max = 100)
    private String slug;

    // 描述
    @TextLength(max = 255)
    private String description;

    // 排序权重
    @NotNull
    @Builder.Default
    private Integer sortOrder = 0;

    // 状态
    @NotNull
    @Min(0)
    @Max(1)
    @Builder.Default
    private Integer status = 1;

    public void setCategoryName(String value) {
        categoryName = value == null ? null : value.strip();
    }

    public void setSlug(String value) {
        slug = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        description = value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
