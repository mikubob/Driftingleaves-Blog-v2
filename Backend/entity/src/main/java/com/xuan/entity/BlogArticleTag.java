package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 文章多标签；禁止逗号拼接标签ID；复合键为 article_id, tag_id，不提供单 ID CRUD。 */
@Data
@TableName("blog_article_tag")
public class BlogArticleTag {
    /** 文章ID，关联blog_article.article_id。 */
    @TableField("article_id")
    private Long articleId;

    /** 标签ID，关联blog_tag.tag_id。 */
    @TableField("tag_id")
    private Long tagId;
}
