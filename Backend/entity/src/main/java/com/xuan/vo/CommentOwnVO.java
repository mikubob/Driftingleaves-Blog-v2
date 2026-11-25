package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本人只拿自己的评论资料，不顺带返回文章正文或其他用户资料。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CommentOwnVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论ID
    private Long commentId;

    // 文章ID
    private Long articleId;

    // 评论用户ID
    private Long userId;

    // 父评论ID
    private Long parentId;

    // 所属文章标题
    private String articleTitle;

    // 所属文章别名
    private String articleSlug;

    // 根评论ID
    private Long rootParentId;

    // 所在页码
    private Integer page;

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
}
