package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 后台按友链名称、网址、状态、来源和申请人筛选。
 */
public record LinkQueryDTO(@Min(1) @Max(100000) Integer page,
                           @Min(1) @Max(100) Integer pageSize,
                           @TextLength(min = 1, max = 100) String keyword,
                           @Min(0) @Max(2) Integer status,
                           @Pattern(regexp = "APPLICATION|ADMIN|UNKNOWN") String source,
                           String applicantId) {

    public LinkQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        keyword = keyword == null ? null : keyword.strip();
    }
}
