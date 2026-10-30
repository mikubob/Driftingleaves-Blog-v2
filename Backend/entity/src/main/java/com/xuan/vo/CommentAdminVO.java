package com.xuan.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台比本人模型多文章标题和作者展示资料，占位作者仍返回 null。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentAdminVO extends CommentOwnVO {

    private ArticlePublicVO.Author author;
    private String articleTitle;
}
