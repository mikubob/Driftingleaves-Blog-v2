package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 公开文章只收这些筛选条件，ID 先保留字符串，入口检查通过后再交给业务层。
 */
public record ArticleQueryDTO(@Min(1) @Max(100000) Integer page,
                              @Min(1) @Max(100) Integer pageSize,
                              @TextLength(min = 1, max = 100) String keyword,
                              String categoryId, String tagId,
                              @Pattern(regexp = "[1-9][0-9]{3}-(0[1-9]|1[0-2])") String month) {

    public ArticleQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
