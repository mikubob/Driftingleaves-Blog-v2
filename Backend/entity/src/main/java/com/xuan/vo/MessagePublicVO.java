package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公开留言数据及当前读者可见的全部回复数量，只在全部上级留言可见时返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言ID
    private Long messageId;

    // 父级ID
    private Long parentId;

    // 作者信息
    private ArticlePublicVO.Author author;

    // 内容
    private String content;

    // HTML内容
    private String htmlContent;

    // 内容格式
    private Integer contentFormat;

    // 是否悄悄话
    private Integer isSecret;

    // 状态
    private Integer status;

    // 编辑时间
    private LocalDateTime editedAt;

    // 是否本人记录
    private boolean isOwn;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // 属地信息
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

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
