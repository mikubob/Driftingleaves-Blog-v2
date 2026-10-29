package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

/** 文章元数据；发布、可见范围与回收站状态独立。 */
@Data
@TableName("blog_article")
public class BlogArticle {
    /** 文章ID。 */
    @TableId(value = "article_id", type = IdType.AUTO)
    private Long articleId;

    /** 创建时取登录用户，不信任客户端提交的作者。 */
    @TableField("author_id")
    private Long authorId;

    /** 所属分类ID，未分类或分类删除后为NULL。 */
    @TableField("category_id")
    private Long categoryId;

    /** 文章标题。 */
    @TableField("title")
    private String title;

    /** 文章URL别名，唯一且区分大小写。 */
    @TableField("slug")
    private String slug;

    /** 文章摘要，用于列表及搜索结果展示。 */
    @TableField("summary")
    private String summary;

    /** 文章封面图URL。 */
    @TableField("cover_url")
    private String coverUrl;

    /** 1原创 2转载 3翻译。 */
    @TableField("article_type")
    private Integer articleType;

    /** 原文链接，转载或翻译时必填。 */
    @TableField("original_url")
    private String originalUrl;

    /** 0草稿 1已发布 2下架。 */
    @TableField("status")
    private Integer status;

    /** 1公开 2私密；私密仅作者或具备相应ALL权限者可读。 */
    @TableField("visibility")
    private Integer visibility;

    /** 是否置顶：0否 1是。 */
    @TableField("is_top")
    private Integer isTop;

    /** 是否允许评论：0否 1是。 */
    @TableField("allow_comment")
    private Integer allowComment;

    /** 浏览计数；点赞/评论数由明细聚合。 */
    @TableField("view_count")
    private BigInteger viewCount;

    /** 首次发布时写入，编辑不覆盖。 */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /** NULL正常，非NULL回收站；slug仍保留。 */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
