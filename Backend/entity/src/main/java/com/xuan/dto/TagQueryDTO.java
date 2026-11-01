package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台按标签名称查找，关键词里的百分号也只当普通文字。
 */
public record TagQueryDTO(@Min(1) @Max(100000) Integer page,
                          @Min(1) @Max(100) Integer pageSize,
                          @TextLength(min = 1, max = 100) String keyword) {

    public TagQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        keyword = keyword == null ? null : keyword.strip();
    }
}
