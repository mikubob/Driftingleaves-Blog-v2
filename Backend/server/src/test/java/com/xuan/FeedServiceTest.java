package com.xuan;

import com.xuan.entity.BlogArticle;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.mapper.FeedMapper;
import com.xuan.mapper.SiteConfigMapper;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.service.impl.FeedServiceImpl;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 直接生成 Feed XML，数据库查询使用替身，检查转义和非法字符清理。 */
class FeedServiceTest {

    private final FeedMapper feeds = mock(FeedMapper.class);
    private final SiteConfigMapper configs = mock(SiteConfigMapper.class);
    private final PublicSiteProperties sites = new PublicSiteProperties();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final FeedServiceImpl service = new FeedServiceImpl(feeds, configs, sites, json, clock);

    @Test
    void rssEscapesAllXmlCharactersAndReplacesInvalidCodePoints() throws Exception {
        sites.setBlogBaseUrl("https://blog.example.test/a&b");
        BlogSiteConfig config = new BlogSiteConfig();
        config.setIsPublic(1);
        config.setConfigValue(json.writeValueAsString(Map.of("title", "站点", "description", "介绍")));
        when(configs.selectById("site")).thenReturn(config);
        BlogArticle article = new BlogArticle();
        article.setSlug("example");
        article.setTitle("<>&\"'中🙂\u0000\uD800");
        article.setPublishTime(LocalDateTime.of(2026, 11, 1, 0, 0));
        when(feeds.recent(any(LocalDateTime.class))).thenReturn(List.of(article));

        String xml = service.rss();

        assertThat(xml).contains("<title>&lt;&gt;&amp;&quot;&apos;中🙂��</title>",
                "<description></description>", "https://blog.example.test/a&amp;b/articles/example");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(document.getElementsByTagName("title").item(1).getTextContent()).isEqualTo("<>&\"'中🙂��");
    }

    @Test
    void sitemapEscapesConfiguredUrls() throws Exception {
        sites.setBlogBaseUrl("https://blog.example.test/a&b");
        when(feeds.sitemap(anyInt(), anyLong(), any(LocalDateTime.class))).thenReturn(List.of());

        String xml = service.sitemap(null);

        assertThat(xml).contains("<loc>https://blog.example.test/a&amp;b</loc>");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(document.getElementsByTagName("loc").item(0).getTextContent())
                .isEqualTo("https://blog.example.test/a&b");
    }
}
