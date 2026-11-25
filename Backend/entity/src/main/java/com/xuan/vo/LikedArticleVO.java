package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本人点赞时间与完整公开文章资料，不包含他人的点赞明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikedArticleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 点赞时间
    private LocalDateTime likedAt;

    // 文章信息
    private ArticlePublicVO article;
}
