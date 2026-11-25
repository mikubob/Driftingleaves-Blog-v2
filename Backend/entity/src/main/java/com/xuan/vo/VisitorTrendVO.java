package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 根据当前保留的明细统计，所有计数用十进制字符串返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 日期
    private LocalDate date;

    // 新增访客数
    private String newVisitorCount;

    // 累计新增访客数
    private String cumulativeNewVisitorCount;
}
