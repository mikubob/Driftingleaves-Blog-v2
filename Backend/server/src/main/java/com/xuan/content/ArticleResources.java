package com.xuan.content;

import com.xuan.client.AliOssClient;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AliOssProperties;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.FileProperties;
import com.xuan.utils.IdUtils;
import com.xuan.utils.MarkdownUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import lombok.RequiredArgsConstructor;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 这里找出文章真正引用的附件，整理本站附件地址，再确认私有对象可用。
 */
@Component
@RequiredArgsConstructor
public class ArticleResources {

    private static final Pattern CONTEXT = Pattern.compile(
            "/api/v1/blog/articles/([1-9][0-9]{0,18})/files/([1-9][0-9]{0,18})/content");
    private static final Parser PARSER = Parser.builder().build();
    private final AuthProperties auth;
    private final FileProperties files;
    private final AliOssProperties ossProperties;
    private final ObjectProvider<AliOssClient> oss;

    /**
     * 保存清理后的 HTML、封面和实际引用 ID，Markdown 原文仍和文章一起保存。
     */
    public record References(String coverUrl, String html, Set<Long> fileIds) {

    }

    /**
     * 从 Markdown 语法树和清理后的 HTML 中找链接、图片，普通数字和代码块不能算附件引用。
     *
     * @param articleId 当前文章；创建为null，此时不允许任何本站附件
     * @param markdown 原始正文，不修改客户端Markdown源
     * @param cover 封面，可为空；外部封面必须是合法HTTP(S)
     * @return 规范公开上下文HTML及去重引用集合；跨篇和编码歧义返回400
     */
    public References inspect(Long articleId, String markdown, String cover) {
        Set<Long> ids = new TreeSet<>();
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void visit(Link link) {
                canonical(link.getDestination(), articleId, ids);
                visitChildren(link);
            }

            /**
             * {@inheritDoc}
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

    /** 后台 HTML 和封面只改写已经确认的本站附件地址，不动 Markdown 原文或外部地址。 */
    public String adminUrl(String value) {
        return value != null && CONTEXT.matcher(value).matches()
                ? value.replace("/api/v1/blog/articles/", "/api/v1/admin/articles/") : value;
    }

    /** 只改 HTML 属性里规范的本站附件地址，不能改正文、外部 URL 或代码示例。 */
    public String adminHtml(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        for (Element element : document.select("a[href], img[src]")) {
            String attribute = element.normalName().equals("a") ? "href" : "src";
            element.attr(attribute, adminUrl(element.attr(attribute)));
        }
        return document.body().html();
    }

    /**
     * 文件行锁和权限检查通过后再查实际对象；文件缺失或不可读返回 503，不抓取外部 URL。
     *
     * @param file 数据库元数据，不能使用客户端存储键或Bucket
     */
    public void requireAvailable(BlogFile file) {
        if ("oss".equals(file.getStorageType())) {
            AliOssClient client = oss.getIfAvailable();
            if (client == null || !file.getBucket().equals(ossProperties.getBucket()) || !client.exists(
                    file.getObjectKey())) {
                throw unavailable();
            }
            return;
        }
        try {
            Path target = localTarget(file);
            if (!Files.isRegularFile(target) || !Files.isReadable(target)
                    || file.getSizeBytes().signum() <= 0 || !file.getSizeBytes()
                    .equals(BigInteger.valueOf(Files.size(target)))) {
                throw unavailable();
            }
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 上传临时文件也放在实际根目录，后面的原子移动才不会跨文件系统。 */
    public Path temporary() {
        try {
            Files.createDirectories(files.getLocalRoot());
            return Files.createTempFile(files.getLocalRoot().toRealPath(), ".upload-", ".tmp");
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 已验证的临时图片写入现有存储；这里只生成键，绝不使用展示文件名作为路径。 */
    public void store(Path temporary, String extension, BlogFile file) {
        AliOssClient client = oss.getIfAvailable();
        if (client != null) {
            file.setStorageType("oss");
            file.setBucket(ossProperties.getBucket());
            try {
                file.setObjectKey(client.upload(Files.readAllBytes(temporary), extension));
            } catch (IOException exception) {
                throw unavailable();
            }
            return;
        }
        file.setStorageType("local");
        file.setBucket("");
        file.setObjectKey(TokenUtils.random() + "." + extension);
        try {
            Files.move(temporary, localTarget(file), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 在文件及文章锁内读取有界字节，关闭流后才返回，存储缺失或大小变化统一 503。 */
    public byte[] read(BlogFile file) {
        if (file.getSizeBytes().signum() <= 0 || file.getSizeBytes().compareTo(BigInteger.valueOf(10 * 1024 * 1024)) > 0
                || !("image/png".equals(file.getMimeType()) || "image/jpeg".equals(file.getMimeType()))) {
            throw unavailable();
        }
        try (InputStream input = "oss".equals(file.getStorageType())
                ? ossClient(file).open(file.getObjectKey()) : Files.newInputStream(localTarget(file))) {
            byte[] bytes = input.readNBytes(10 * 1024 * 1024 + 1);
            if (!file.getSizeBytes().equals(BigInteger.valueOf(bytes.length))) {
                throw unavailable();
            }
            return bytes;
        } catch (IOException | BusinessException exception) {
            throw unavailable();
        }
    }

    /** 已不存在的对象也算清理完成，供数据库提交失败后的 DELETE 重试使用。 */
    public void delete(BlogFile file) {
        if ("oss".equals(file.getStorageType())) {
            ossClient(file).delete(file.getObjectKey());
            return;
        }
        try {
            Files.deleteIfExists(localTarget(file));
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 临时文件和回滚补偿清理失败要留固定运维错误，不能把路径或云端响应写进日志。 */
    public void cleanup(Path temporary, BlogFile stored) {
        try {
            if (stored != null && stored.getObjectKey() != null) {
                delete(stored);
            }
        } catch (RuntimeException exception) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "文件补偿清理失败，需要检查孤立对象");
        }
        try {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "上传临时文件清理失败，需要运维清理");
        }
    }

    /** 元数据中的 Bucket 也必须匹配当前受管存储，不能借历史记录访问别的空间。 */
    private AliOssClient ossClient(BlogFile file) {
        AliOssClient client = oss.getIfAvailable();
        if (client == null || !ossProperties.getBucket().equals(file.getBucket())) {
            throw unavailable();
        }
        return client;
    }

    /** 已有文件必须检查真实路径，缺失对象仍检查父目录，保证重试删除不会走出根目录。 */
    private Path localTarget(BlogFile file) throws IOException {
        if (!"local".equals(file.getStorageType()) || !"".equals(file.getBucket())
                || file.getObjectKey() == null
                || !file.getObjectKey().matches("(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.(?:png|jpg|jpeg)")) {
            throw unavailable();
        }
        Path root = files.getLocalRoot().toRealPath();
        Path target = root.resolve(file.getObjectKey()).normalize();
        Path parent = target.getParent();
        // 历史子目录也可能已被清走，检查最近仍存在的上级，再允许重试删除缺失对象。
        while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            parent = parent.getParent();
        }
        if (!target.startsWith(root) || !parent.toRealPath().startsWith(root)
                || Files.isSymbolicLink(target)
                || (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!target.toRealPath().startsWith(root) || !Files.isRegularFile(target)))) {
            throw unavailable();
        }
        return target;
    }

    /** 只有明确配置的本站 origin 和相对路径才按内部附件处理，外部 URL 不转成 fileId。 */
    private String canonical(String value, Long articleId, Set<Long> ids) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        String host = uri.getHost();
        if (uri.getRawUserInfo() != null) {
            throw invalid();
        }
        if (host != null && storageHost(host)) {
            throw invalid();
        }
        if (host != null && !siteOrigin(uri)) {
            return value;
        }
        String raw = uri.getRawPath();
        if (raw == null) {
            return value;
        }
        String decoded = raw;
        try {
            String previous;
            do {
                previous = decoded;
                decoded = URLDecoder.decode(decoded.replace("+", "%2B"), StandardCharsets.UTF_8);
            } while (!decoded.equals(previous) && decoded.contains("%"));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        String normalized;
        try {
            normalized = URI.create("/" + decoded.replace('\\', '/').replaceFirst("^/+", "")).normalize().getPath();
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        String original = decoded.toLowerCase(Locale.ROOT).replace('\\', '/');
        boolean attachment = lower.matches(".*/files(?:[/;].*|$)") || original.matches(".*/files(?:[/;].*|$)")
                || lower.startsWith("/uploads/") || lower.startsWith("/upload/");
        if (!attachment) {
            return value;
        }
        Matcher match = CONTEXT.matcher(raw);
        if (!match.matches() || !raw.equals(decoded) || !raw.equals(normalized)
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                || (uri.isAbsolute() && !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(
                uri.getScheme())))
                || (uri.getRawAuthority() != null && !uri.isAbsolute()) || articleId == null
                || !articleId.equals(IdUtils.parse(match.group(1)))) {
            throw invalid();
        }
        ids.add(IdUtils.parse(match.group(2)));
        return raw;
    }

    /** 本站 origin 从部署配置取，不能根据请求 Host 或转发头临时认定。 */
    private boolean siteOrigin(URI uri) {
        Set<String> origins = new HashSet<>(auth.getAllowedOrigins());
        origins.addAll(files.getPublicOrigins());
        return origins.stream().map(URI::create).anyMatch(origin -> origin.getHost() != null
                && origin.getHost().equalsIgnoreCase(uri.getHost())
                && origin.getScheme().equalsIgnoreCase(uri.getScheme() == null ? origin.getScheme() : uri.getScheme())
                && port(origin) == port(uri));
    }

    /** 比较 origin 时，HTTP 和 HTTPS 的默认端口按同一个地址处理。 */
    private int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    /** 配置过的 OSS endpoint 和 bucket 域名也不能拿来做公开直链绕过文章附件地址。 */
    private boolean storageHost(String host) {
        String endpoint = ossProperties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        String storage = URI.create(endpoint).getHost();
        return host.equalsIgnoreCase(storage) || host.equalsIgnoreCase(ossProperties.getBucket() + "." + storage);
    }

    /** 输入有问题只报固定错误，不透露 URL、存储键或私密文件是否存在。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "附件引用或封面URL不符合本站上下文约束");
    }

    /** 对象没了或存储出错都要失败，不能拿空对象当作发布成功。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "引用文件存储暂不可用");
    }
}
