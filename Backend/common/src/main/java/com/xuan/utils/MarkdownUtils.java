package com.xuan.utils;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Markdown 先渲染再清理 HTML，标题锚点、字数和阅读时间也在这里生成。
 */
public final class MarkdownUtils {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(),
            AutolinkExtension.create(), TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{M}\\p{N}_\\s-]",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WORD = Pattern.compile("\\p{IsHan}|[A-Za-z0-9]+");

    private MarkdownUtils() {
    }

    /**
     * Markdown 转成 HTML 后还要清理，开头有标签也不能当成可信 HTML。
     *
     * @param markdown 原始 Markdown；null 和空文本输出空字符串
     * @return 可输出的安全 HTML，含表格、代码、任务列表及标题锚点
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = PARSER.parse(markdown);
        return sanitize(RENDERER.render(document));
    }

    /**
     * 旧 HTML 缓存也要重新清理，去掉脚本、事件、样式和危险 URL，再生成不重复的标题 ID。
     *
     * @param html 原始 HTML，不可信
     * @return 清理后的正文；允许规范相对 URL 和同页锚点
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Safelist allowed = Safelist.relaxed()
                .removeProtocols("a", "href", "ftp")
                .addProtocols("a", "href", "#")
                .addTags("input")
                .addAttributes("input", "checked")
                .addEnforcedAttribute("input", "type", "checkbox")
                .addEnforcedAttribute("input", "disabled", "disabled")
                .addAttributes("code", "class")
                .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
                .preserveRelativeLinks(true);
        // 检查相对地址需要一个 base，最后仍返回原相对地址，不把这个占位地址带出去。
        String clean = Jsoup.clean(html, "https://markdown.invalid/", allowed);
        Document document = Jsoup.parseBodyFragment(clean);
        Set<String> used = new HashSet<>();
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6")) {
            String base = PUNCTUATION.matcher(heading.text().strip().toLowerCase(Locale.ROOT)).replaceAll("");
            base = WHITESPACE.matcher(base).replaceAll("-");
            if (base.isEmpty()) {
                base = "section";
            }
            String id = base;
            int suffix = 0;
            while (!used.add(id)) {
                id = base + "-" + ++suffix;
            }
            heading.id(id);
        }
        return document.body().html();
    }

    /**
     * 按清理后能看到的正文计算字数，每个汉字算一字，连续英文字母或数字算一个词。
     *
     * @param markdown 原始 Markdown，代码块和链接 URL 目标不计数
     * @return 安全 HTML、字数和至少一分钟的阅读时长；不保存数据库计数副本
     */
    public static ArticleContent article(String markdown) {
        String html = toHtml(markdown);
        Document document = Jsoup.parseBodyFragment(html);
        document.select("pre").remove();
        long count = WORD.matcher(document.body().text()).results().count();
        return new ArticleContent(html, count, (int) Math.max(1, (count + 299) / 300));
    }

    /**
     * 正文结果不依赖业务模型；ID 和计数如何转成字符串由应用负责。
     */
    public record ArticleContent(String htmlContent, long wordCount, int readingMinutes) {

    }
}
