package com.xuan.vo;

/** 文章详情和版本一起传递，版本只写到 HTTP 响应头。 */
public record ArticleProfileVO(ArticleAdminDetailVO article, String revision) { }
