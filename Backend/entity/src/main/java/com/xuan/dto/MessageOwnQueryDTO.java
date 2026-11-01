package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 本人分页不能传入userId。
 */
public record MessageOwnQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
                                 @Min(0) @Max(3) Integer status) {

    public MessageOwnQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
