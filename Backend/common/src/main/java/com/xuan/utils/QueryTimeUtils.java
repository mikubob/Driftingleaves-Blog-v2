package com.xuan.utils;

import java.time.DateTimeException;

import com.xuan.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 明细筛选采用UTC毫秒时间，数据库DATETIME范围之外的输入直接拒绝。 */
public final class QueryTimeUtils {
    private QueryTimeUtils() { }
    /** 将接口Time转换为数据库UTC值，不接受隐式本地时区。 */
    public static LocalDateTime parse(String value) {
        if (value == null) { return null; }
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")) { throw new IllegalArgumentException(); }
            LocalDateTime result = LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
            if (result.getYear() < 1000 || result.getYear() > 9999) { throw new IllegalArgumentException(); }
            return result;
        } catch (IllegalArgumentException | DateTimeException error) {
            throw new BusinessException(400, "VALIDATION_ERROR", "时间须为合法UTC毫秒时间");
        }
    }
    /** 明细筛选使用[begin,end)，单侧界限允许缺失。 */
    public static void requireRange(LocalDateTime begin, LocalDateTime end) {
        if (begin != null && end != null && !begin.isBefore(end)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "begin须早于end");
        }
    }
}
