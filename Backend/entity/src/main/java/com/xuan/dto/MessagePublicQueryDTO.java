package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询根留言或同一讨论的全部回复。
 */
public record MessagePublicQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(50) Integer pageSize,
                                    String parentId) {

    public MessagePublicQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }

}
