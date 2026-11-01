package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * userId 只是筛评论者，不能扩大当前 query 权限的文章作者范围。
 */
public record CommentAdminQueryDTO(@Min(1) @Max(100000) Integer page,
                                   @Min(1) @Max(50) Integer pageSize,
                                   String articleId, String userId, @Min(0) @Max(3) Integer status) {

    public CommentAdminQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
