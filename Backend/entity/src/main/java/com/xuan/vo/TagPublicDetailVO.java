package com.xuan.vo;

/**
 * 标签详情只统计当前公开文章。
 */
public record TagPublicDetailVO(Long tagId, String tagName, String slug, String articleCount) {

}
