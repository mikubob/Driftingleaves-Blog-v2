package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 根据当前保留的明细统计，所有计数用十进制字符串返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleViewMetricVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    private Long articleId;

    // 标题
    private String title;

    // URL别名
    private String slug;

    // 浏览量
    private String pageViewCount;
}
