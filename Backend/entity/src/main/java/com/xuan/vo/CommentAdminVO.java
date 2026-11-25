package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * 后台评论包含作者展示资料，文章标题按后台权限返回。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommentAdminVO extends CommentOwnVO {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论作者信息
    private ArticlePublicVO.Author author;

    // 后台列表是否有直接回复，详情未查询时为null
    private Boolean hasChildren;

    // 被回复目标信息
    private DiscussionReplyTargetVO replyTarget;

    // 是否文章作者
    private boolean articleAuthor;

    // 是否站点作者
    private boolean siteAuthor;

    // 是否站点管理员
    private boolean siteAdmin;
}
