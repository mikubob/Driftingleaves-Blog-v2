package com.xuan.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/** 后台详情把正文和关联一起取出，要用同一快照或文章锁事务。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleAdminDetailVO extends ArticleAdminVO {
    private Long categoryId;
    private List<Long> tagIds;
    private String markdownContent;
    private String htmlContent;
}
