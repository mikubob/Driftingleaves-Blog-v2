package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台按分类名称和状态筛选，页码没填就从第一页开始。
 */
public record CategoryQueryDTO(@Min(1) @Max(100000) Integer page,
                               @Min(1) @Max(100) Integer pageSize,
                               @TextLength(min = 1, max = 100) String keyword,
                               @Min(0) @Max(1) Integer status) {

    public CategoryQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        keyword = keyword == null ? null : keyword.strip();
    }
}
