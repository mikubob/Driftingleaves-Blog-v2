package com.xuan.vo;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 热门榜只返回符合公开条件的文章，hotValue 是所选指标当前的累计数。
 */
public record HotArticleVO(Long articleId, String title, String slug, String summary, String coverUrl,
                           BigInteger viewCount, Long likeCount, BigInteger hotValue, LocalDateTime publishTime) {

}
