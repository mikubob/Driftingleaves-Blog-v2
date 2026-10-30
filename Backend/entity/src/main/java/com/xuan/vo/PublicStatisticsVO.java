package com.xuan.vo;



/** 统计口径以保留明细为准，所有计数用十进制字符串。 */
public record PublicStatisticsVO(String articleCount, String categoryCount, String tagCount, String pageViewCount, String todayPageViewCount, String visitorCount) { }
