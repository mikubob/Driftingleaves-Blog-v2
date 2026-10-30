package com.xuan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 后台私密留言仅在本次query具有ALL时可筛选。 */
public record MessageAdminQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
        @Min(0) @Max(3) Integer status, String userId, @Min(0) @Max(1) Integer isSecret, String begin, String end) {
    public MessageAdminQueryDTO { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
}
