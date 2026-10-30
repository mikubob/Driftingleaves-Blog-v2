package com.xuan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 本人分页不能传入userId。 */
public record MessageOwnQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize, @Min(0) @Max(3) Integer status) {
    public MessageOwnQueryDTO { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
}
