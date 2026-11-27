package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 访问来源统计，按来源域名聚合，无来源时 source 为空字符串（直接访问）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefererMetricVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 来源域名，空串表示直接访问
    private String source;

    // 浏览量
    private String pageViewCount;

    // 独立访客数
    private String visitorCount;
}
