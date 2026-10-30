package com.xuan.model;

import java.time.LocalDateTime;

/**
 * SQL 使用已校验的条件；scopeAuthor 来自权限，不能被客户端 authorId 替换，公开查询固定 publicOnly。
 */
public record ArticleFilter(boolean publicOnly, Long scopeAuthor, String keyword, Long categoryId,
                            Long tagId, Long authorId, Integer status, Integer visibility, int trash,
                            LocalDateTime from, LocalDateTime until, LocalDateTime now) {

}
