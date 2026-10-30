package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
/** 展示资源共用名称与启停筛选，入口另查允许的查询字段。 */
public record DisplayQueryDTO(@Min(1) @Max(100000) Integer page,
        @Min(1) @Max(100) Integer pageSize, @TextLength(min = 1, max = 100) String keyword,
        @Min(0) @Max(1) Integer status) {
    public DisplayQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
