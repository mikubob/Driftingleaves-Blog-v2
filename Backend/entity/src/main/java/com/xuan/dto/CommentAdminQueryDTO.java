package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 查询后台评论，parentId 为0时查根节点，未传时保留平铺分页；userId 不扩大文章范围。
 */
public record CommentAdminQueryDTO(@Min(1) @Max(100000) Integer page,
                                   @Min(1) @Max(50) Integer pageSize,
                                   String articleId, String userId, @Min(0) @Max(2) Integer status,
                                   String parentId) {

    public CommentAdminQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
