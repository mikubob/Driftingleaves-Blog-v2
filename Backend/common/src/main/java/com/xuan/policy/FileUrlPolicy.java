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
 * 文件地址校验，文章和音乐共用本站来源、附件路径和存储直链规则。
 */
@Component
@RequiredArgsConstructor
public class FileUrlPolicy {

    private final AuthProperties auth;
    private final FileProperties files;
    private final AliOssProperties ossProperties;

    /** 解析本站附件路径，普通链接和外部地址返回 null，拒绝存储直链和编码不明确的地址。 */
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

    /** 判断是否为本站地址，只使用部署配置，不相信请求 Host 或转发头。 */
    public boolean siteOrigin(URI uri) {
        Set<String> origins = new HashSet<>(auth.getAllowedOrigins());
        origins.addAll(files.getPublicOrigins());
        return origins.stream().map(URI::create).anyMatch(origin -> origin.getHost() != null
                && origin.getHost().equalsIgnoreCase(uri.getHost())
                && origin.getScheme().equalsIgnoreCase(uri.getScheme() == null ? origin.getScheme() : uri.getScheme())
                && port(origin) == port(uri));
    }

    /** 获取地址端口，未指定时使用 HTTP 或 HTTPS 对应的默认端口。 */
    private int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    /** 判断是否为存储域名，OSS endpoint 和 Bucket 地址都不能绕过附件接口直接公开。 */
    private boolean storageHost(String host) {
        String endpoint = ossProperties.getEndpoint();
        if (StrUtil.nullToEmpty(endpoint).isBlank()) {
            return false;
        }
        String storage = URI.create(endpoint).getHost();
        return host.equalsIgnoreCase(storage) || host.equalsIgnoreCase(ossProperties.getBucket() + "." + storage);
    }

    /** 返回附件地址错误，不带原 URL、存储路径或私密文件的存在信息。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "附件地址无效，请从本站文件列表重新选择，不要填写存储直链或修改附件地址。");
    }
}
