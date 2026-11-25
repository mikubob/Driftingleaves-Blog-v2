package com.xuan.model;

import com.xuan.entity.BlogMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * 查询时连接公开资料和展示身份，不读取邮箱或 IP。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MessageRow extends BlogMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言作者昵称
    private String authorNickname;

    // 留言作者头像
    private String authorAvatar;

    // 是否有子回复
    private Boolean hasChildren;

    // 回复数量
    private Long replyCount;

    // 被回复人昵称
    private String replyToNickname;

    // 被回复内容
    private String replyToContent;

    // 当前用户是否可回复
    private Boolean replyable;

    // 是否站点作者
    private boolean siteAuthor;

    // 是否站点管理员
    private boolean siteAdmin;
}
