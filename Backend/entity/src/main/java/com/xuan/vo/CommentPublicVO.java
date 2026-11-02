package com.xuan.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公开评论只返回审核通过的记录，不含评论者邮箱或待审内容。
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
