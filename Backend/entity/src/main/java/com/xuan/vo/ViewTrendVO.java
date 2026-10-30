package com.xuan.vo;

import java.time.LocalDate;

/**
 * 统计口径以保留明细为准，所有计数用十进制字符串。
 */
public record ViewTrendVO(LocalDate date, String pageViewCount, String visitorCount) {

}
