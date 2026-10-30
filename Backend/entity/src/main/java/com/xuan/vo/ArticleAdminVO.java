package com.xuan.vo;

import com.xuan.entity.BlogCategory;
import com.xuan.entity.BlogTag;
import lombok.Data;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/** 后台列表只给文章资料，不带大段正文和单条版本。 */
@Data
public class ArticleAdminVO {
    private Long articleId;
    private Long authorId;
    private Author author;
    private BlogCategory category;
    private String title;
    private String slug;
    private String summary;
    private String coverUrl;
    private Integer articleType;
    private String originalUrl;
    private Integer status;
    private Integer visibility;
    private Integer isTop;
    private Integer allowComment;
    private BigInteger viewCount;
    private Long likeCount;
    private Long commentCount;
    private LocalDateTime publishTime;
    private LocalDateTime deletedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<BlogTag> tags;

    /** 后台作者资料只返回约定字段，不带头像、邮箱和认证信息。 */
    public record Author(Long userId, String nickname) { }
}
