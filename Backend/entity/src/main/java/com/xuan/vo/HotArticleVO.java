package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 热门榜只返回符合公开条件的文章，hotValue 是所选指标当前的累计数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    private Long articleId;

    // 标题
    private String title;

    // URL别名
    private String slug;

    // 摘要
    private String summary;

    // 封面图URL
    private String coverUrl;

    // 浏览量
    private BigInteger viewCount;

    // 点赞数
    private Long likeCount;

    // 热度值
    private BigInteger hotValue;

    // 发布时间
    private LocalDateTime publishTime;
}
