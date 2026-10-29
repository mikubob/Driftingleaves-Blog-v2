package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 文章评论与回复；删除时清空内容为占位文案并置status=3，保留回复链。 */
@Data
@TableName("blog_comment")
public class BlogComment {
    /** 评论ID。 */
    @TableId(value = "comment_id", type = IdType.AUTO)
    private Long commentId;

    /** 所属文章ID，关联blog_article.article_id。 */
    @TableField("article_id")
    private Long articleId;

    /** 仅登录用户评论。 */
    @TableField("user_id")
    private Long userId;

    /** 被回复的评论ID，必须属于同一文章。 */
    @TableField("parent_id")
    private Long parentId;

    /** 纯文本或经过白名单过滤的内容。 */
    @TableField("content")
    private String content;

    /** 0待审核 1通过 2驳回 3作者删除占位。 */
    @TableField("status")
    private Integer status;

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
