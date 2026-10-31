package com.xuan.cache;

import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;

/** 已补默认值的查询模型按固定字段序列化，slug 保留大小写。 */
@Component("publicCacheKeys")
@RequiredArgsConstructor
public class PublicCacheKeys {
    private final Clock clock;
    private final JsonMapper codec = JsonMapper.builder().build();
    public String key(String method, Object[] args) {
        String calendar = "rankings".equals(method) ? YearMonth.from(clock.instant().atOffset(ZoneOffset.UTC)).toString()
                : "publicStatistics".equals(method) ? LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString() : "";
        return TokenUtils.digest(method + ":" + calendar + ":" + codec.writeValueAsString(args));
    }
}
