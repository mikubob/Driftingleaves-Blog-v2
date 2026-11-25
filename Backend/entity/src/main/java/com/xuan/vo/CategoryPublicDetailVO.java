package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分类详情附带公开文章数，不改变既有分类列表的字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryPublicDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 分类ID
    private Long categoryId;

    // 分类名称
    private String categoryName;

    // URL别名
    private String slug;

    // 描述
    private String description;

    // 排序权重
    private Integer sortOrder;

    // 文章数
    private String articleCount;
}
