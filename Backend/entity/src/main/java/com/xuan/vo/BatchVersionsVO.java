package com.xuan.vo;

import java.util.List;

/** 结果严格对应用户原始选择顺序。 */
public record BatchVersionsVO(List<BatchVersionVO> items) {
}
