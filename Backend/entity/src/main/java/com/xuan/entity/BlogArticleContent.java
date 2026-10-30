package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 正文单独存一张表，列表查询就不用读取大段内容。 */
@Data
@TableName("blog_article_content")
public class BlogArticleContent {
    /** 文章ID，同时作为主键及关联blog_article.article_id的外键。 */
    @TableId(value = "article_id", type = IdType.INPUT)
    private Long articleId;

    /** Markdown格式的文章正文。 */
    @TableField("markdown_content")
    private String markdownContent;

    /** HTML 可以留作渲染缓存，但输出前仍要清理不安全内容。 */
    @TableField("html_content")
    private String htmlContent;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
