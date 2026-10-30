package com.xuan.vo;

import lombok.Data;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/** 前台只返回公开文章资料，不带 Markdown、后台状态和作者私人资料。 */
@Data
public class ArticlePublicVO {
    private Long articleId;
    private Author author;
    private Category category;
    private String title;
    private String slug;
    private String summary;
    private String coverUrl;
    private Integer articleType;
    private String originalUrl;
    private Integer isTop;
    private Integer allowComment;
    private BigInteger viewCount;
    private Long likeCount;
    private Long commentCount;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<Tag> tags;

    /** 作者只返回公开展示资料。 */
    public record Author(Long userId, String nickname, String avatar) { }
    /** 分类隐藏时，这里返回 null。 */
    public record Category(Long categoryId, String categoryName, String slug, String description, Integer sortOrder) { }
    /** 标签按 ID 从小到大返回。 */
    public record Tag(Long tagId, String tagName, String slug) { }
    /** 上下篇和推荐只返回定位文章需要的资料。 */
    public record Navigation(Long articleId, String title, String slug) { }
}
