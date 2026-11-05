package com.xuan.model;

import com.xuan.entity.BlogMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询时连接公开资料和展示身份，不读取邮箱或 IP。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageRow extends BlogMessage {

    private String authorNickname;
    private String authorAvatar;
    private Boolean hasChildren;
    private Long replyCount;
    private String replyToNickname;
    private String replyToContent;
    private Boolean replyable;
    private boolean siteAuthor;
    private boolean siteAdmin;
}
