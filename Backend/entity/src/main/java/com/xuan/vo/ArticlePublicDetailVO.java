package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.List;

/**
 * 公开详情补充安全正文和阅读信息，查询上下篇和推荐不会增加阅读量。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArticlePublicDetailVO extends ArticlePublicVO {

    @Serial
    private static final long serialVersionUID = 1L;

    // Markdown正文内容
    private String markdownContent;

    // 字数统计
    private Long wordCount;

    // 预计阅读时间（分钟）
    private Integer readingMinutes;

    // 上一篇文章
    private Navigation prevArticle;

    // 下一篇文章
    private Navigation nextArticle;

    // 相关推荐文章列表
    private List<Navigation> relatedArticles;
}
