package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 简历经历按类型、标题与状态筛选，公开入口不接受后台字段。
 */
public record ExperienceQueryDTO(@Min(1) @Max(100000) Integer page,
                                 @Min(1) @Max(100) Integer pageSize, @TextLength(min = 1, max = 100) String keyword,
                                 @Min(0) @Max(1) Integer status, @Min(0) @Max(2) Integer experienceType) {

    public ExperienceQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
