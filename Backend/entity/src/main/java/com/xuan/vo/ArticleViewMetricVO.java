package com.xuan.vo;

/**
 * 根据当前保留的明细统计，所有计数用十进制字符串返回。
 */
public record ArticleViewMetricVO(Long articleId, String title, String slug, String pageViewCount) {

}
