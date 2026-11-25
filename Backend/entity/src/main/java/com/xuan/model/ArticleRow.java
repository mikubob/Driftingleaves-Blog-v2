package com.xuan.model;

import com.xuan.entity.BlogArticle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 这里接收文章与作者展示资料的联表结果，不直接拿去做 HTTP 响应。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArticleRow extends BlogArticle {

    @Serial
    private static final long serialVersionUID = 1L;

    // 作者昵称
    private String authorNickname;

    // 作者头像
    private String authorAvatar;

    // 点赞数
    private Long likeCount;

    // 点赞时间
    private LocalDateTime likedAt;
}
