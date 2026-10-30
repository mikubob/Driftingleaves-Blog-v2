package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 这里只存文章基本资料；发布、公开或私密、回收站分别控制。
 */
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

    /** 1公开 2私密；私密文章只能由作者或有对应 ALL 权限的人读取。 */
    @TableField("visibility")
    private Integer visibility;

    /** 是否置顶：0否 1是。 */
    @TableField("is_top")
    private Integer isTop;

    /** 是否允许评论：0否 1是。 */
    @TableField("allow_comment")
    private Integer allowComment;

    /** 浏览量保存在这里，点赞和评论数从明细计算。 */
    @TableField("view_count")
    private BigInteger viewCount;

    /** 首次发布时写入，编辑不覆盖。 */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /** NULL正常，非NULL回收站；slug仍保留。 */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
