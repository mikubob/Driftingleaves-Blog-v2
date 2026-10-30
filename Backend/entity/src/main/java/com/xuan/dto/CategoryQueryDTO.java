package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 后台按分类名称和状态筛选，页码没填就从第一页开始。 */
public record CategoryQueryDTO(@Min(1) @Max(100000) Integer page,
                               @Min(1) @Max(100) Integer pageSize,
                               @TextLength(min = 1, max = 100) String keyword,
                               @Min(0) @Max(1) Integer status) {
    public CategoryQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
