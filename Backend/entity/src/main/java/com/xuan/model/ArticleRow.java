package com.xuan.model;

import com.xuan.entity.BlogArticle;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 这里接收文章与作者展示资料的联表结果，不直接拿去做 HTTP 响应。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleRow extends BlogArticle {
    private String authorNickname;
    private String authorAvatar;
    private Long likeCount;
}
