package com.xuan.mapper;

import com.xuan.entity.BlogArticle;
import com.xuan.model.SitemapRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feed和站点地图直接读取PUBLIC文章，不依赖邮箱订阅或阅读计数。
 */
public interface FeedMapper {

    /** 最近二十篇公开文章，读取不增加view_count。 */
    List<BlogArticle> recent(@Param("now") LocalDateTime now);

    /** 按种类和主键分批取所有公开资源，保持一次快照。 */
    List<SitemapRow> sitemap(@Param("kind") int kind, @Param("after") long after, @Param("now") LocalDateTime now);
}
