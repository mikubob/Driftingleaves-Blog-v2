package com.xuan.policy;

import com.xuan.exception.BusinessException;
import com.xuan.policy.FileUrlPolicy;
import com.xuan.policy.UrlPolicy;
import com.xuan.utils.IdUtils;
import com.xuan.utils.MarkdownUtils;
import lombok.RequiredArgsConstructor;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章附件工具，解析正文和封面引用，转换后台展示地址。
 */
@Component
@RequiredArgsConstructor
public class ArticleAttachmentPolicy {

    private static final Pattern CONTEXT = Pattern.compile(
            "/api/v1/blog/articles/([1-9][0-9]{0,18})/files/([1-9][0-9]{0,18})/content");
    private static final Parser PARSER = Parser.builder().build();

    private final FileUrlPolicy urls;

    /**
     * 文章附件检查结果，包含清理后的 HTML、封面和引用id；Markdown 原文仍随文章保存。
     */
    public record References(String coverUrl, String html, Set<Long> fileIds) {

    }

    /**
     * 查询正文和封面中的附件引用，检查 Markdown 和清理后的 HTML，不把普通数字或代码块当附件。
     *
     * @param articleId 当前文章；创建为null，此时不允许任何本站附件
     * @param markdown 原始正文，不修改客户端Markdown源
     * @param cover 封面，可为空；外部封面必须是合法HTTP(S)
     * @return 清理后的 HTML 和去重后的附件id；跨文章引用或编码不明确时返回 400
     */
    public References inspect(Long articleId, String markdown, String cover) {
        Set<Long> ids = new TreeSet<>();
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            /**
             * 检查 Markdown 链接或图片中的附件引用。
             */
            @Override
            public void visit(Link link) {
                canonical(link.getDestination(), articleId, ids);
                visitChildren(link);
            }

            /**
             * 检查 Markdown 链接或图片中的附件引用。
             */
            @Override
            public void visit(Image image) {
                canonical(image.getDestination(), articleId, ids);
                visitChildren(image);
            }
        });
        Document document = Jsoup.parseBodyFragment(MarkdownUtils.toHtml(markdown));
        for (Element element : document.select("a[href], img[src]")) {
            String attribute = element.normalName().equals("a") ? "href" : "src";
            element.attr(attribute, canonical(element.attr(attribute), articleId, ids));
        }
        String normalizedCover = cover == null ? null : canonical(cover, articleId, ids);
        if (normalizedCover != null && !CONTEXT.matcher(normalizedCover).matches() && !UrlPolicy.valid(
                normalizedCover)) {
            throw invalid();
        }
        return new References(normalizedCover, document.body().html(), Set.copyOf(ids));
    }

    /** 将本站附件地址转为后台地址，不修改 Markdown 原文或外部链接。 */
    public String adminUrl(String value) {
        return value != null && CONTEXT.matcher(value).matches()
                ? value.replace("/api/v1/blog/articles/", "/api/v1/admin/articles/") : value;
    }

    /** 转换 HTML 中的本站附件地址，只改链接属性，不修改正文、外部链接或代码示例。 */
    public String adminHtml(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        for (Element element : document.select("a[href], img[src]")) {
            String attribute = element.normalName().equals("a") ? "href" : "src";
            element.attr(attribute, adminUrl(element.attr(attribute)));
        }
        return document.body().html();
    }

    /** 检查并整理文章附件地址，本站附件必须属于当前文章，外部地址保持原样。 */
    private String canonical(String value, Long articleId, Set<Long> ids) {
        String path = urls.attachmentPath(value);
        if (path == null) {
            return value;
        }
        Matcher match = CONTEXT.matcher(path);
        if (!match.matches() || articleId == null || !articleId.equals(IdUtils.parse(match.group(1)))) {
            throw invalid();
        }
        ids.add(IdUtils.parse(match.group(2)));
        return path;
    }

    /** 返回附件地址错误，不带原 URL、存储路径或私密文件的存在信息。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "附件引用或封面URL不符合本站上下文约束");
    }
}
