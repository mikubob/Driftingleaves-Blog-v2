package com.xuan.properties;

import cn.hutool.core.util.StrUtil;
import com.xuan.exception.BusinessException;
import com.xuan.policy.UrlPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 公开站点地址配置，用于生成订阅链接和检查访问来源、路径，不使用请求 Host 头。
 */
@Data
@ConfigurationProperties("app.sites")
public class PublicSiteProperties {

    private String homeBaseUrl;
    private String blogBaseUrl;
    private String adminBaseUrl;
    private String cvBaseUrl;
    private String apiBaseUrl;

    /** 获取站点地址，缺少配置时拒绝相关请求，不推测部署域名。 */
    public URI base(String code) {
        String value = switch (code) {
            case "home" -> homeBaseUrl;
            case "blog" -> blogBaseUrl;
            case "admin" -> adminBaseUrl;
            case "cv" -> cvBaseUrl;
            case "api" -> apiBaseUrl;
            default -> null;
        };
        try {
            if (value == null || !UrlPolicy.valid(value)) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(value);
            if (uri.getQuery() != null || uri.getFragment() != null || !uri.normalize().equals(uri)
                    || uri.getRawPath().contains("%") || uri.getRawPath().contains("//")) {
                throw new IllegalArgumentException();
            }
            return URI.create(value.replaceAll("/+$", ""));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "公开站点基址未正确配置");
        }
    }

    /** 从站点基址获取来源，不使用代理转发头。 */
    public String origin(String code) {
        return originOf(base(code));
    }

    public static String originOf(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        int defaultPort = "https".equals(scheme) ? 443 : 80;
        return scheme + "://" + host + (port < 0 || port == defaultPort ? "" : ":" + port);
    }

    /** 根据来源和路径确定站点，使用已配置的 Origin 和最长匹配前缀，避免同域子站混淆。 */
    public void requireSite(String code, String origin, String path) {
        URI source;
        try {
            source = URI.create(origin == null ? "" : origin);
            if (source.getScheme() == null || source.getHost() == null || source.getRawUserInfo() != null
                    || source.getRawQuery() != null || source.getRawFragment() != null
                    || StrUtil.isNotEmpty(source.getPath())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new BusinessException(403, "FORBIDDEN", "采集来源不被允许");
        }
        base(code);
        String sourceOrigin = originOf(source);
        String match = null;
        int longest = -1;
        for (String candidate : List.of("home", "blog", "cv")) {
            String configured = switch (candidate) {
                case "home" -> homeBaseUrl;
                case "blog" -> blogBaseUrl;
                default -> cvBaseUrl;
            };
            if (StrUtil.nullToEmpty(configured).isBlank()) {
                continue;
            }
            URI value = base(candidate);
            String prefix = value.getPath();
            if (originOf(value).equals(sourceOrigin)
                    && (StrUtil.isEmpty(prefix) || path.equals(prefix) || path.startsWith(prefix + "/"))) {
                if (prefix.length() == longest) {
                    throw new BusinessException(503, "SERVICE_UNAVAILABLE", "公开站点来源与路径配置重叠");
                }
                if (prefix.length() > longest) {
                    longest = prefix.length();
                    match = candidate;
                }
            }
        }
        if (!code.equals(match)) {
            throw new BusinessException(403, "FORBIDDEN", "采集站点与来源路径不匹配");
        }
    }

    /** 生成文章前台地址，访问采集和订阅链接使用相同规则。 */
    public String articleUrl(String slug) {
        return base("blog").toString() + "/articles/" + slug;
    }
}
