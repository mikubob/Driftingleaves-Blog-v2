package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.List;

/**
 * 后台文章详情，同时返回正文和关联资料，必须在同一个只读事务或文章加锁事务中读取。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArticleAdminDetailVO extends ArticleAdminVO {

    @Serial
    private static final long serialVersionUID = 1L;

    // 分类ID
    private Long categoryId;

    // 标签ID列表
    private List<Long> tagIds;

    // Markdown正文内容
    private String markdownContent;
}
