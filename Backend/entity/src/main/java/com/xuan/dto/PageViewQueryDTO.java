package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 明细筛选时间为[begin,end)，路径不接受查询参数与fragment。
 */
public record PageViewQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
                               String visitorId, String userId, String articleId,
                               @Pattern(regexp = "home|blog|cv") String siteCode,
                               @TextLength(min = 1, max = 2048) String pagePath,
                               @TextLength(min = 1, max = 2048) String referer,
                               String begin, String end) {

    public PageViewQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
}
