package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SQL 使用已校验的条件；scopeAuthor 来自权限，不能被客户端 authorId 替换，公开查询固定 publicOnly。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFilter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 是否仅查询公开已发布文章
    private boolean publicOnly;

    // 权限限定的作者ID
    private Long scopeAuthor;

    // 搜索关键词
    private String keyword;

    // 分类ID
    private Long categoryId;

    // 标签ID
    private Long tagId;

    // 客户端筛选的作者ID
    private Long authorId;

    // 文章状态
    private Integer status;

    // 可见性
    private Integer visibility;

    // 回收站标记
    private int trash;

    // 发布时间起点
    private LocalDateTime from;

    // 发布时间终点
    private LocalDateTime until;

    // 当前时间
    private LocalDateTime now;
}
