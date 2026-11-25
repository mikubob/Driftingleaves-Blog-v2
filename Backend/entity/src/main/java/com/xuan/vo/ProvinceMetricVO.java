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
public class ProvinceMetricVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 省份
    private String province;

    // 访客数
    private String visitorCount;
}
