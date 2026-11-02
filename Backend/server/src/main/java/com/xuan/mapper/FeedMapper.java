package com.xuan.mapper;

import com.xuan.entity.BlogArticle;
import com.xuan.model.SitemapRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RSS 和站点地图查询接口，只读取公开文章，不依赖邮件订阅或阅读计数。
 */
public interface FeedMapper {

    /** 最近二十篇公开文章，读取不增加view_count。 */
    List<BlogArticle> recent(@Param("now") LocalDateTime now);

    /** 分批查询站点地图所需的全部公开资料，按种类和主键读取，放在同一个只读事务中。 */
    List<SitemapRow> sitemap(@Param("kind") int kind, @Param("after") long after, @Param("now") LocalDateTime now);
}
