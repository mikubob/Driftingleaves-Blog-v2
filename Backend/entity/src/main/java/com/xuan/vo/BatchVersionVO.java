package com.xuan.vo;

/** 一个资源写入后的版本，不携带业务详情。 */
public record BatchVersionVO(Long id, String revision) {
}
