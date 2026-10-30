package com.xuan.model;

import com.xuan.entity.BlogComment;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** SQL 只连接展示所需字段，不能把整条用户资料带进响应。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentRow extends BlogComment {
    private String authorNickname;
    private String authorAvatar;
    private String articleTitle;
    private Long replyCount;
}
