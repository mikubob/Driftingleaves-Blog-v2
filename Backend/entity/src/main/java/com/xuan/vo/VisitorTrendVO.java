package com.xuan.vo;

import java.time.LocalDate;

/**
 * 根据当前保留的明细统计，所有计数用十进制字符串返回。
 */
public record VisitorTrendVO(LocalDate date, String newVisitorCount, String cumulativeNewVisitorCount) {

}
