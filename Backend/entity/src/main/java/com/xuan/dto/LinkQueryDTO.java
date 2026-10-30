package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台按友链名称和状态筛选，不把 keyword 当 SQL 通配符。
 */
public record LinkQueryDTO(@Min(1) @Max(100000) Integer page,
                           @Min(1) @Max(100) Integer pageSize,
                           @TextLength(min = 1, max = 100) String keyword,
                           @Min(0) @Max(2) Integer status) {

    public LinkQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
