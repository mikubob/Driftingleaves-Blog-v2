package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台私密留言仅在本次query具有ALL时可筛选。
 */
public record MessageAdminQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
                                   @Min(0) @Max(3) Integer status, String userId, @Min(0) @Max(1) Integer isSecret,
                                   String begin, String end) {

    public MessageAdminQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
