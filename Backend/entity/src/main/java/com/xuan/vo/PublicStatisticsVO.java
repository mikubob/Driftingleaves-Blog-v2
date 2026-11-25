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
public class PublicStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章数
    private String articleCount;

    // 分类数
    private String categoryCount;

    // 标签数
    private String tagCount;

    // 浏览量
    private String pageViewCount;

    // 今日浏览量
    private String todayPageViewCount;

    // 访客数
    private String visitorCount;
}
