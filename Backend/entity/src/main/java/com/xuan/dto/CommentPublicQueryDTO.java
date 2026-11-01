package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 评论逐层分页，不传父节点就只查根评论。
 */
public record CommentPublicQueryDTO(@Min(1) @Max(100000) Integer page,
                                    @Min(1) @Max(50) Integer pageSize, String parentId) {

    public CommentPublicQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
