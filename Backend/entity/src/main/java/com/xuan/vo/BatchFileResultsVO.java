package com.xuan.vo;

import java.util.List;

/** 文件对象不能跨项回滚，逐项报告结果。 */
public record BatchFileResultsVO(List<BatchFileResultVO> results) {
}
