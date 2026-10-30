package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 访客原始清单仅向ALL后台提供，不接受任意排序。 */
public record VisitorQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
        @TextLength(min = 1, max = 64) String country, @TextLength(min = 1, max = 64) String province,
        @TextLength(min = 1, max = 64) String city, @Min(0) @Max(1) Integer isBlocked) {
    public VisitorQueryDTO { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
}
