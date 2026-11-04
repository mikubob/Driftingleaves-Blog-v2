package com.xuan.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章评论返回审核通过的记录及当前用户自己的待审、驳回评论，不含邮箱。
 */
@Data
public class CommentPublicVO {

    private Long commentId;
    private Long articleId;
    private Long parentId;
    private String content;
    private String location;
    private String userAgentOs;
    private String userAgentBrowser;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private ArticlePublicVO.Author author;
    private Long replyCount;
}
