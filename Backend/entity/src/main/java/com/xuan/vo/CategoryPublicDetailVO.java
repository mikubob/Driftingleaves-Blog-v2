package com.xuan.vo;

/**
 * 分类详情附带公开文章数，不改变既有分类列表的字段。
 */
public record CategoryPublicDetailVO(Long categoryId, String categoryName, String slug, String description,
                                     Integer sortOrder, String articleCount) {

}
