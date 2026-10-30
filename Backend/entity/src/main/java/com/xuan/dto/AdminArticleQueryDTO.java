package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** authorId 只是额外筛选条件，不能借它看到 query 权限以外的文章。 */
public record AdminArticleQueryDTO(@Min(1) @Max(100000) Integer page,
                                   @Min(1) @Max(100) Integer pageSize,
                                   @TextLength(min = 1, max = 100) String keyword,
                                   String categoryId, String tagId, String authorId,
                                   @Min(0) @Max(2) Integer status,
                                   @Min(1) @Max(2) Integer visibility,
                                   @Min(0) @Max(1) Integer trash) {
    public AdminArticleQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        trash = trash == null ? 0 : trash;
        keyword = keyword == null ? null : keyword.strip();
    }
}
