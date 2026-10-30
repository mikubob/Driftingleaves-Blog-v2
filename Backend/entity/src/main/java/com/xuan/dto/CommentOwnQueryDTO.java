package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 本人查询不能传 userId，下架文章的评论也保留。
 */
public record CommentOwnQueryDTO(@Min(1) @Max(100000) Integer page,
                                 @Min(1) @Max(50) Integer pageSize,
                                 String articleId, @Min(0) @Max(3) Integer status) {

    public CommentOwnQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
}
