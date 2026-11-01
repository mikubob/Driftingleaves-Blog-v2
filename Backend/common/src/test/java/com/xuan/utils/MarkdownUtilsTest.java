package com.xuan.utils;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实际解析和清理 HTML，检查危险标签、URL、字数和阅读时间。
 */
class MarkdownUtilsTest {

    @Test
    void nullHandlingPreservesExistingUnicodeWhitespaceRules() {
        assertThat(MarkdownUtils.toHtml(null)).isEmpty();
        assertThat(MarkdownUtils.sanitize(null)).isEmpty();
        assertThat(MarkdownUtils.toHtml(" \t\n\u3000")).isEmpty();
        assertThat(MarkdownUtils.sanitize(" \t\n\u3000")).isEmpty();
        assertThat(MarkdownUtils.toHtml("\u00a0")).isNotEmpty();
        assertThat(MarkdownUtils.toHtml("\u200c")).isNotEmpty();
        assertThat(MarkdownUtils.sanitize("\u00a0")).isNotEmpty();
        assertThat(MarkdownUtils.sanitize("\u200c")).isNotEmpty();
    }

    @Test
    void sanitizesExecutableHtmlAndUnsafeUrlsIncludingEncodedProtocols() {
        String html = MarkdownUtils.toHtml("""
                <script>alert('secret')</script>
                <iframe src="https://evil.test"></iframe>
                <a href="jav&#x61;script:alert(1)" onclick="alert(2)" style="color:red">危险</a>
                <img src="data:image/svg+xml,evil" onerror="alert(3)">
                <a href="vbscript:evil">危险2</a>
                <a href="https://safe.test">安全</a>
                """);
        var body = Jsoup.parseBodyFragment(html);
        assertThat(body.select("script, iframe, [onclick], [onerror], [style]")).isEmpty();
        assertThat(html).doesNotContain("javascript:", "vbscript:", "data:image", "secret");
        assertThat(body.select("a[href]")).hasSize(1);
        assertThat(body.selectFirst("a[href]").attr("rel")).contains("noopener", "noreferrer");
    }

    @Test
    void preservesExtensionsRelativeResourcesAndUniqueStableAnchors() {
        String html = MarkdownUtils.toHtml("""
                # 标题 Hello!
                # 标题 Hello!
                # 标题 Hello-1

                | a | b |
                |---|---|
                | 1 | 2 |

                - [x] 已完成
                - [ ] 待完成

                [附件](/api/v1/blog/articles/1/files/2/content) [锚点](#标题-hello)

                ```java
                int value = 1;
                ```
                """);
        var body = Jsoup.parseBodyFragment(html);
        assertThat(body.select("h1").eachAttr("id")).containsExactly("标题-hello", "标题-hello-1", "标题-hello-1-1");
        assertThat(body.select("table tbody tr")).hasSize(1);
        assertThat(body.select("input[type=checkbox][disabled]")).hasSize(2);
        assertThat(body.select("input[checked]")).hasSize(1);
        assertThat(body.select("a").eachAttr("href")).containsExactly("/api/v1/blog/articles/1/files/2/content", "#标题-hello");
        assertThat(body.selectFirst("code").className()).isEqualTo("language-java");
        assertThat(MarkdownUtils.sanitize(html)).isEqualTo(html);
    }

    @Test
    void derivesVisibleWordsWithoutMarkdownTargetsOrCodeBlocks() {
        var article = MarkdownUtils.article("中文 Hello123 [世界](https://target.test/hidden) `inline`\n\n```\nignored 你好\n```");
        assertThat(article.wordCount()).isEqualTo(6);
        assertThat(article.readingMinutes()).isEqualTo(1);
        assertThat(MarkdownUtils.article("中".repeat(301)).readingMinutes()).isEqualTo(2);
        assertThat(MarkdownUtils.article(null).wordCount()).isZero();
        assertThat(MarkdownUtils.toHtml("  ")).isEmpty();
    }
}
