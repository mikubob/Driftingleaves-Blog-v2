package com.xuan.policy;

import cn.hutool.core.util.StrUtil;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AliOssProperties;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文章和音乐共用本站 origin、受管文件路径及存储直链检查。
 */
@Component
@RequiredArgsConstructor
public class FileUrlPolicy {

    private final AuthProperties auth;
    private final FileProperties files;
    private final AliOssProperties ossProperties;

    /** 检查本站附件路径；普通链接和外部地址返回 null，存储直链及编码歧义直接拒绝。 */
    public String attachmentPath(String value) {
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
            return null;
        }
        String raw = uri.getRawPath();
        if (raw == null) {
            return null;
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
            return null;
        }
        if (!raw.equals(decoded) || !raw.equals(normalized)
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.isAbsolute() && !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme())))
                || (uri.getRawAuthority() != null && !uri.isAbsolute())) {
            throw invalid();
        }
        return raw;
    }

    /** 本站 origin 从部署配置取，不能根据请求 Host 或转发头临时认定。 */
    public boolean siteOrigin(URI uri) {
        Set<String> origins = new HashSet<>(auth.getAllowedOrigins());
        origins.addAll(files.getPublicOrigins());
        return origins.stream().map(URI::create).anyMatch(origin -> origin.getHost() != null
                && origin.getHost().equalsIgnoreCase(uri.getHost())
                && origin.getScheme().equalsIgnoreCase(uri.getScheme() == null ? origin.getScheme() : uri.getScheme())
                && port(origin) == port(uri));
    }

    /** 比较 origin 时，HTTP 和 HTTPS 的默认端口按同一个地址处理。 */
    private int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    /** 配置过的 OSS endpoint 和 bucket 域名也不能拿来做公开直链绕过文章附件地址。 */
    private boolean storageHost(String host) {
        String endpoint = ossProperties.getEndpoint();
        if (StrUtil.nullToEmpty(endpoint).isBlank()) {
            return false;
        }
        String storage = URI.create(endpoint).getHost();
        return host.equalsIgnoreCase(storage) || host.equalsIgnoreCase(ossProperties.getBucket() + "." + storage);
    }

    /** 输入有问题只报固定错误，不透露 URL、存储键或私密文件是否存在。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "附件引用或封面URL不符合本站上下文约束");
    }
}
