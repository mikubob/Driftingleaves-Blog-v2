package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * authorId 只是额外筛选条件，不能借它看到 query 权限以外的文章。
 */
public record AdminArticleQueryDTO(@Min(1) @Max(100000) Integer page,
                                   @Min(1) @Max(100) Integer pageSize,
                                   @TextLength(min = 1, max = 100) String keyword,
                                   String categoryId, String tagId, String authorId,
                                   @Min(0) @Max(2) Integer status,
                                   @Min(1) @Max(2) Integer visibility,
                                   @Min(0) @Max(1) Integer trash) {

    public AdminArticleQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        trash = ObjectUtil.defaultIfNull(trash, 0);
        keyword = keyword == null ? null : keyword.strip();
    }
}
