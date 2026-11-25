package com.xuan.vo;

import com.xuan.entity.BlogCategory;
import com.xuan.entity.BlogTag;
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
 * 后台列表只给文章资料，不带大段正文和单条版本。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    private Long articleId;

    // 作者ID
    private Long authorId;

    // 作者信息
    private Author author;

    // 分类信息
    private BlogCategory category;

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

    // 文章状态
    private Integer status;

    // 可见性
    private Integer visibility;

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

    // 删除时间
    private LocalDateTime deletedAt;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // 标签列表
    private List<BlogTag> tags;

    /**
     * 后台作者资料只返回约定字段，不带头像、邮箱和认证信息。
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
    }
}
