package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 查询后台留言，parentId 为0时查根节点，未传时保留平铺分页；查询须有ALL范围。
 */
public record MessageAdminQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
                                   @Min(0) @Max(2) Integer status, String userId, @Min(0) @Max(1) Integer isSecret,
                                   String begin, String end, String parentId) {

    public MessageAdminQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
