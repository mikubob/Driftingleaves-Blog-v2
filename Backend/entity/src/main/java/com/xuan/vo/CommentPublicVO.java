package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章评论返回审核通过的记录及当前用户自己的待审、驳回评论，不含邮箱。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论ID
    private Long commentId;

    // 文章ID
    private Long articleId;

    // 父评论ID
    private Long parentId;

    // 评论内容
    private String content;

    // 属地信息
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

    // 审核状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // 评论作者信息
    private ArticlePublicVO.Author author;

    // 回复数量
    private Long replyCount;

    // 被回复目标信息
    private DiscussionReplyTargetVO replyTarget;

    // 是否可回复
    private boolean replyable;

    // 是否站点作者
    private boolean siteAuthor;

    // 是否站点管理员
    private boolean siteAdmin;
}
