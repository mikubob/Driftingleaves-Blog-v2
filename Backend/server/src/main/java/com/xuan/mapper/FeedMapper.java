package com.xuan.mapper;

import com.xuan.entity.BlogArticle;
import com.xuan.model.SitemapRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RSS 与站点地图数据访问接口
 */
@Mapper
public interface FeedMapper {

    /**
     * 查询最近发布的 20 篇公开文章，用于生成 RSS 订阅源。
     *
     * @param now 当前 UTC 时间
     * @return 公开文章列表
     */
    List<BlogArticle> recent(@Param("now") LocalDateTime now);

    /**
     * 按资源类型与游标主键分批查询站点地图条目
     *
     * @param kind 资源类型（1 文章、2 分类、3 标签）
     * @param after 上一批最大主键 ID
     * @param now 当前 UTC 时间
     * @return 站点地图条目列表
     */
    List<SitemapRow> sitemap(@Param("kind") int kind,
            @Param("after") long after,
            @Param("now") LocalDateTime now);
}
