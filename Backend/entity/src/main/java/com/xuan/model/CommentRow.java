package com.xuan.model;

import com.xuan.entity.BlogComment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * SQL 只连接展示所需字段，不能把整条用户资料带进响应。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommentRow extends BlogComment {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论作者昵称
    private String authorNickname;

    // 评论作者头像
    private String authorAvatar;

    // 所属文章标题
    private String articleTitle;

    // 所属文章别名
    private String articleSlug;

    // 回复数量
    private Long replyCount;

    // 是否有子回复
    private Boolean hasChildren;

    // 被回复人昵称
    private String replyToNickname;

    // 被回复内容
    private String replyToContent;

    // 是否站点作者
    private boolean siteAuthor;

    // 是否站点管理员
    private boolean siteAdmin;

    // 是否文章作者
    private boolean articleAuthor;
}
