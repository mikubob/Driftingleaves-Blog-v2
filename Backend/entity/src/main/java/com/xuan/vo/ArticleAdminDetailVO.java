package com.xuan.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 后台文章详情，同时返回正文和关联资料，必须在同一个只读事务或文章加锁事务中读取。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleAdminDetailVO extends ArticleAdminVO {

    private Long categoryId;
    private List<Long> tagIds;
    private String markdownContent;
}
