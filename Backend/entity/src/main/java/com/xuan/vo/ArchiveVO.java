package com.xuan.vo;

/**
 * 按 UTC 月份返回该月符合公开条件的文章数。
 */
public record ArchiveVO(String month, Long articleCount) {

}
