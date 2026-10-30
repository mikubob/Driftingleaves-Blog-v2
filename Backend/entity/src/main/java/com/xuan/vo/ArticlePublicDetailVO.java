package com.xuan.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 公开详情补充安全正文和阅读信息，查询上下篇和推荐不会增加阅读量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticlePublicDetailVO extends ArticlePublicVO {

    private String htmlContent;
    private Long wordCount;
    private Integer readingMinutes;
    private Navigation prevArticle;
    private Navigation nextArticle;
    private List<Navigation> relatedArticles;
}
