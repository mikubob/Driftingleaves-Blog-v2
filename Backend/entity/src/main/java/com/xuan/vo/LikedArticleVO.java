package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 本人点赞时间与完整公开文章资料，不包含他人的点赞明细。
 */
public record LikedArticleVO(LocalDateTime likedAt, ArticlePublicVO article) {

}
