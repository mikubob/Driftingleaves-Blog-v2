package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 删除评论时清空原内容、改成占位文案并设 status=3，回复链要保留。 */
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

    /** 纯文本或只保留安全标签的内容。 */
    @TableField("content")
    private String content;

    /** 0待审核 1通过 2驳回 3作者删除占位。 */
    @TableField("status")
    private Integer status;

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
