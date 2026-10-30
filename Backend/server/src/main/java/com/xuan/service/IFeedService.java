package com.xuan.service;



/** XML公开输出不依赖邮箱订阅，不包Result。 */
public interface IFeedService {
    /** 生成最近二十篇PUBLIC文章RSS2.0，文本安全编码。 */
    String rss();
    /** 根据50000条和50MiB上限输出完整地图、索引或指定子图。 */
    String sitemap(Integer page);
}
