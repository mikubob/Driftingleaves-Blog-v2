package com.xuan.vo;

/**
 * 统计口径以保留明细为准，所有计数用十进制字符串。
 */
public record AdminStatisticsVO(String pageViewCount, String visitorCount, String newVisitorCount, String articleCount,
                                String publishedArticleCount, String commentCount, String messageCount,
                                String pendingCommentCount, String pendingMessageCount) {

}
