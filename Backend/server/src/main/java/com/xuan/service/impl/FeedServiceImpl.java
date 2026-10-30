package com.xuan.service.impl;

import com.xuan.entity.BlogArticle;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.FeedMapper;
import com.xuan.mapper.SiteConfigMapper;
import com.xuan.model.SitemapRow;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.service.IFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** PUBLIC变更即时反映在Feed与地图，不维护另一个缓存或计数。 */
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements IFeedService {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String OPEN = XML + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">";
    private static final String CLOSE = "</urlset>";
    private final FeedMapper feeds;
    private final SiteConfigMapper configs;
    private final PublicSiteProperties sites;
    private final JsonMapper json;
    private final Clock clock;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String rss() {
        String base = sites.base("blog").toString();
        BlogSiteConfig config = configs.selectById("site");
        if (config == null || config.getIsPublic() != 1) { throw new BusinessException(503, "SERVICE_UNAVAILABLE", "公开站点配置不可用"); }
        JsonNode value = json.readTree(config.getConfigValue());
        StringBuilder xml = new StringBuilder(XML).append("<rss version=\"2.0\"><channel>");
        xml.append(tag("title", value.path("title").asString())).append(tag("link", base))
                .append(tag("description", value.path("description").asString()));
        for (BlogArticle row : feeds.recent(now())) {
            String link = sites.articleUrl(row.getSlug());
            xml.append("<item>").append(tag("title", row.getTitle())).append(tag("link", link))
                    .append("<guid isPermaLink=\"true\">").append(escape(link)).append("</guid>")
                    .append(tag("pubDate", DateTimeFormatter.RFC_1123_DATE_TIME.format(row.getPublishTime().atOffset(ZoneOffset.UTC))))
                    .append(tag("description", row.getSummary())).append("</item>");
        }
        return xml.append("</channel></rss>").toString();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String sitemap(Integer requested) {
        int wanted = requested == null ? 1 : requested;
        SitemapPages pages = new SitemapPages(wanted);
        pages.add(sites.base("blog").toString(), null);
        if (sites.getHomeBaseUrl() != null && !sites.getHomeBaseUrl().isBlank()) { pages.add(sites.base("home").toString(), null); }
        if (sites.getCvBaseUrl() != null && !sites.getCvBaseUrl().isBlank()) { pages.add(sites.base("cv").toString(), null); }
        int kind = 0; long after = 0;
        LocalDateTime at = now();
        while (true) {
            List<SitemapRow> batch = feeds.sitemap(kind, after, at);
            if (batch.isEmpty()) { break; }
            for (SitemapRow row : batch) {
                String link = sites.base("blog").toString() + switch (row.getKind()) {
                    case 1 -> "/articles/"; case 2 -> "/categories/"; case 3 -> "/tags/"; default -> throw new IllegalStateException("未知地图资源");
                } + row.getSlug();
                pages.add(link, row.getUpdatedAt()); kind = row.getKind(); after = row.getResourceId();
            }
        }
        if (wanted > pages.page) { throw new BusinessException(404, "NOT_FOUND", "站点地图页不存在"); }
        if (requested != null || pages.page == 1) { return OPEN + pages.body + CLOSE; }
        StringBuilder index = new StringBuilder(XML).append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        // 子图链接明确指向已存在的后端接口，不依赖额外的前端路由。
        for (int page = 1; page <= pages.page; page++) {
            index.append("<sitemap>").append(tag("loc", sites.base("api").toString() + "/blog/sitemap.xml?page=" + page)).append("</sitemap>");
        }
        return index.append("</sitemapindex>").toString();
    }

    /** XML1.0不合法控制字符替换为替代符，再转义文本与属性特殊字符。 */
    private static String escape(String value) {
        if (value == null) { return ""; }
        StringBuilder clean = new StringBuilder();
        value.codePoints().forEach(c -> clean.appendCodePoint(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF ? c : 0xFFFD));
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    /** 所有来自资料表的文本都经过同一个XML编码。 */
    private static String tag(String name, String value) { return "<" + name + ">" + escape(value) + "</" + name + ">"; }
    /** 不依赖执行机器本地时区。 */
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    /** 每次只保留需要返回的一页正文，扫描全部资源用于决定是否输出索引。 */
    private static final class SitemapPages {
        private final int wanted;
        private final StringBuilder body = new StringBuilder();
        private int page = 1;
        private int count;
        private int bytes = OPEN.getBytes(StandardCharsets.UTF_8).length + CLOSE.length();
        private SitemapPages(int wanted) { this.wanted = wanted; }
        /** XML编码后的实际字节计入50MiB限制，不能按Java字符数估算。 */
        private void add(String link, LocalDateTime updated) {
            String entry = "<url>" + tag("loc", link) + (updated == null ? "" : tag("lastmod", updated.atOffset(ZoneOffset.UTC).toString())) + "</url>";
            int size = entry.getBytes(StandardCharsets.UTF_8).length;
            if (count == 50000 || bytes + size > 50 * 1024 * 1024) {
                page++; count = 0; bytes = OPEN.getBytes(StandardCharsets.UTF_8).length + CLOSE.length();
            }
            if (page == wanted) { body.append(entry); }
            count++; bytes += size;
        }
    }
}
