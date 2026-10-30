package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 后台按标签名称查找，关键词里的百分号也只当普通文字。 */
public record TagQueryDTO(@Min(1) @Max(100000) Integer page,
                         @Min(1) @Max(100) Integer pageSize,
                         @TextLength(min = 1, max = 100) String keyword) {
    public TagQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
