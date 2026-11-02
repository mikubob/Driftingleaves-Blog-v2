package com.xuan.cache;

import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

/** 生成公开查询缓存键，保留完整筛选条件和大小写。 */
@Component("publicCacheKeyGenerator")
@RequiredArgsConstructor
public class PublicCacheKeyGenerator {
    private final Clock clock;
    private final JsonMapper codec = JsonMapper.builder().build();

    /** 将方法和参数转成摘要，排行榜和统计额外带上 UTC 月份或日期。 */
    public String key(String method, Object[] args) {
        String calendar = "";
        if ("rankings".equals(method)) {
            calendar = YearMonth.from(clock.instant().atOffset(ZoneOffset.UTC)).toString();
        } else if ("publicStatistics".equals(method)) {
            calendar = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
        }
        return TokenUtils.digest(method + ":" + calendar + ":" + codec.writeValueAsString(args));
    }
}
