package com.xuan.vo;

/**
 * 根据当前保留的明细统计，所有计数用十进制字符串返回。
 */
public record PublicStatisticsVO(String articleCount, String categoryCount, String tagCount, String pageViewCount,
                                 String todayPageViewCount, String visitorCount) {

}
