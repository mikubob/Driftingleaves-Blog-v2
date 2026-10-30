package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 文章可以有多个标签，每条关联用 article_id、tag_id 一起确定，不能拼成逗号字符串或用单个 ID 做增删改查。 */
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
