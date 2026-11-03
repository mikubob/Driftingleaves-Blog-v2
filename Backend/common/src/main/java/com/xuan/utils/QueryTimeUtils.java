package com.xuan.utils;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.exception.BusinessException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 查询时间工具，使用 UTC 毫秒时间，拒绝超出数据库 DATETIME 范围的值。
 */
public final class QueryTimeUtils {

    private QueryTimeUtils() {
    }

    /** 将接口时间转为数据库 UTC 时间，不使用本地时区。 */
    public static LocalDateTime parse(String value) {
        if (ObjectUtil.isNull(value)) {
            return null;
        }
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")) {
                throw new IllegalArgumentException();
            }
            LocalDateTime result = LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
            if (result.getYear() < 1000 || result.getYear() > 9999) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (IllegalArgumentException | DateTimeException error) {
            throw new BusinessException(400, "VALIDATION_ERROR", "时间格式无效，请重新选择日期和时间。");
        }
    }

    /** 检查查询时间范围，包含开始时刻、不包含结束时刻，可以只传一端。 */
    public static void requireRange(LocalDateTime begin, LocalDateTime end) {
        if (ObjectUtil.isNotNull(begin) && ObjectUtil.isNotNull(end) && !begin.isBefore(end)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "开始时间必须早于结束时间，请调整时间范围。");
        }
    }
}
