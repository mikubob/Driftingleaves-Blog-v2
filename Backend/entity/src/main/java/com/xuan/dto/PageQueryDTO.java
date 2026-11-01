package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 公开字典只需要页码和每页条数，不接收后台筛选条件。
 */
public record PageQueryDTO(@Min(1) @Max(100000) Integer page,
                          @Min(1) @Max(100) Integer pageSize) {
    public PageQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
