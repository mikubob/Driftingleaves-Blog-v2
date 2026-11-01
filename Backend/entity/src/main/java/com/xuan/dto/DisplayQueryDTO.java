package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 展示资源共用名称与启停筛选，入口另查允许的查询字段。
 */
public record DisplayQueryDTO(@Min(1) @Max(100000) Integer page,
                              @Min(1) @Max(100) Integer pageSize, @TextLength(min = 1, max = 100) String keyword,
                              @Min(0) @Max(1) Integer status) {

    public DisplayQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        keyword = keyword == null ? null : keyword.strip();
    }
}
