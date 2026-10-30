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

/** Markdown 正文处理；统一渲染、白名单清理、稳定锚点及读取时派生的阅读信息。 */
public final class MarkdownUtils {
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(),
            AutolinkExtension.create(), TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{M}\\p{N}_\\s-]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WORD = Pattern.compile("\\p{IsHan}|[A-Za-z0-9]+");

    private MarkdownUtils() { }

    /**
     * 解析 Markdown 后实际清理 HTML，不自动把以标签起始的正文视为可信 HTML。
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
     * 重新清理已有 HTML 缓存，删除脚本、事件、样式及危险 URL，重新生成唯一标题 ID。
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
        // 相对资源需显式 base 才能校验协议；最终只输出原相对 URL，不暴露该占位源。
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
     * 从安全渲染后的可见正文派生阅读信息，每个汉字、连续英文字母/数字词计一。
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

    /** 无业务模型依赖的正文派生结果，HTTP ID/计数字符串化由应用负责。 */
    public record ArticleContent(String htmlContent, long wordCount, int readingMinutes) { }
}
