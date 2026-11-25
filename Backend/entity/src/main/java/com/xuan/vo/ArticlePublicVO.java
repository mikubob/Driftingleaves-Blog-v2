package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台只返回公开文章资料，不带 Markdown、后台状态和作者私人资料。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    private Long articleId;

    // 作者信息
    private Author author;

    // 分类信息
    private Category category;

    // 文章标题
    private String title;

    // 文章URL别名
    private String slug;

    // 文章摘要
    private String summary;

    // 封面图URL
    private String coverUrl;

    // 文章类型
    private Integer articleType;

    // 原文链接
    private String originalUrl;

    // 是否置顶
    private Integer isTop;

    // 是否允许评论
    private Integer allowComment;

    // 浏览量
    private BigInteger viewCount;

    // 点赞数
    private Long likeCount;

    // 评论数
    private Long commentCount;

    // 发布时间
    private LocalDateTime publishTime;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // 标签列表
    private List<Tag> tags;

    /**
     * 作者只返回公开展示资料。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // 用户ID
        private Long userId;

        // 昵称
        private String nickname;

        // 头像URL
        private String avatar;
    }

    /**
     * 分类隐藏时，这里返回 null。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // 分类ID
        private Long categoryId;

        // 分类名称
        private String categoryName;

        // 分类别名
        private String slug;

        // 分类描述
        private String description;

        // 排序权重
        private Integer sortOrder;
    }

    /**
     * 标签按 ID 从小到大返回。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tag implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // 标签ID
        private Long tagId;

        // 标签名称
        private String tagName;

        // 标签别名
        private String slug;
    }

    /**
     * 上下篇和推荐只返回定位文章需要的资料。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Navigation implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // 文章ID
        private Long articleId;

        // 文章标题
        private String title;

        // 文章URL别名
        private String slug;
    }
}
