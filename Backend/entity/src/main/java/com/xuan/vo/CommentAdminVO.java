package com.xuan.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台比本人模型多文章标题和作者展示资料。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentAdminVO extends CommentOwnVO {

    private ArticlePublicVO.Author author;
    private String articleTitle;
    /** 后台列表是否有直接回复，详情未查询时为null。 */
    private Boolean hasChildren;
    private DiscussionReplyTargetVO replyTarget;
    private boolean articleAuthor;
    private boolean siteAuthor;
    private boolean siteAdmin;
}
