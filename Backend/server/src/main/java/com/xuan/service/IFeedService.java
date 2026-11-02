package com.xuan.service;

/**
 * RSS 和站点地图业务接口，公开返回 XML，不放进 Result，也不依赖邮件订阅。
 */
public interface IFeedService {

    /** 生成最近二十篇PUBLIC文章RSS2.0，文本安全编码。 */
    String rss();

    /** 根据50000条和50MiB上限输出完整地图、索引或指定子图。 */
    String sitemap(Integer page);
}
