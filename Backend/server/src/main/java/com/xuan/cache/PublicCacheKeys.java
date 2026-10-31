package com.xuan.cache;

import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

/** 已补默认值的查询模型按固定字段序列化，slug 保留大小写。 */
@Component("publicCacheKeys")
@RequiredArgsConstructor
public class PublicCacheKeys {
    private final Clock clock;
    private final JsonMapper codec = JsonMapper.builder().build();

    /** 查询参数保留完整筛选，排行榜和统计额外带上对应的 UTC 月份或日期。 */
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
