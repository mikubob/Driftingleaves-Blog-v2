package com.xuan.constant;

/**
 * 缓存域与缓存管理器名称常量，供缓存配置和业务层注解共用，防止拼写不一致导致失效遗漏。
 */
public final class CacheNames {

    /**
     * 轻量 Redis 缓存管理器 Bean 名称，用于后台统计、IP 封禁和用户授权等非写保护域。
     */
    public static final String SIMPLE_CACHE_MANAGER = "simpleCacheManager";

    public static final String HOME = "home";
    public static final String SOCIAL = "social";
    public static final String EXPERIENCES = "experiences";
    public static final String SKILLS = "skills";
    public static final String LINKS = "links";
    public static final String MUSIC = "music";
    public static final String CONFIGS = "configs";
    public static final String CATEGORIES = "categories";
    public static final String TAGS = "tags";
    public static final String AUTHORS = "authors";
    public static final String ARCHIVES = "archives";
    public static final String RSS = "rss";
    public static final String SITEMAP = "sitemap";
    public static final String ARTICLES = "articles";
    public static final String SEARCH = "search";
    public static final String ARTICLE_DETAIL = "articleDetail";
    public static final String RANKINGS = "rankings";
    public static final String COMMENTS = "comments";
    public static final String MESSAGES = "messages";
    public static final String STATISTICS = "statistics";

    /**
     * 后台实时统计报表缓存域（短 TTL）。
     */
    public static final String ADMIN_STATS = "adminStats";

    /**
     * 后台历史区间统计报表缓存域（结束日期早于今日 UTC，长 TTL）。
     */
    public static final String ADMIN_STATS_HISTORY = "adminStatsHistory";

    /**
     * 访客 IP 手动封禁状态缓存域（写路径提交后精准失效）。
     */
    public static final String IP_BLOCK = "ipBlock";

    /**
     * 后台用户角色与权限授权缓存域（权限变更提交后精准失效）。
     */
    public static final String USER_AUTHZ = "userAuthz";

    private CacheNames() {
    }
}
