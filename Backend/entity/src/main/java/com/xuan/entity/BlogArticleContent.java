package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 正文一对一拆分，列表查询无需读取大字段。 */
@Data
@TableName("blog_article_content")
public class BlogArticleContent {
    /** 文章ID，同时作为主键及关联blog_article.article_id的外键。 */
    @TableId(value = "article_id", type = IdType.INPUT)
    private Long articleId;

    /** Markdown格式的文章正文。 */
    @TableField("markdown_content")
    private String markdownContent;

    /** 可选渲染缓存；输出前由应用过滤不安全HTML。 */
    @TableField("html_content")
    private String htmlContent;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
