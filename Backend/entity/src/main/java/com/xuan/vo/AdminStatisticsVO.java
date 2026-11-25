package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 根据当前保留的明细统计，所有计数用十进制字符串返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 浏览量
    private String pageViewCount;

    // 访客数
    private String visitorCount;

    // 新增访客数
    private String newVisitorCount;

    // 文章数
    private String articleCount;

    // 已发布文章数
    private String publishedArticleCount;

    // 评论数
    private String commentCount;

    // 留言数量
    private String messageCount;

    // 待审核评论数
    private String pendingCommentCount;

    // 待审核留言数
    private String pendingMessageCount;
}
