package com.xuan.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 本人只拿自己的评论资料，不顺带返回文章正文或其他用户资料。 */
@Data
public class CommentOwnVO {
    private Long commentId;
    private Long articleId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
