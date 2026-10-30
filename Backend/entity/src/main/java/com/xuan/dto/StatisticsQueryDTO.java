package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * UTC自然日区间由业务层成对检查，排行入口仅接收limit而不接收siteCode。
 */
public record StatisticsQueryDTO(String begin, String end, @Pattern(regexp = "home|blog|cv") String siteCode,
                                 @Min(1) @Max(100) Integer limit) {

    public StatisticsQueryDTO {
        limit = limit == null ? 10 : limit;
    }
}
