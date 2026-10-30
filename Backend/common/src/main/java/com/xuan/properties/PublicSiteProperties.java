package com.xuan.properties;

import com.xuan.exception.BusinessException;
import com.xuan.utils.UrlPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

/**
 * 三个公开站点的固定基址，用于Feed链接与采集来源/路径核验，不能信任Host头。
 */
@Data
@ConfigurationProperties("app.sites")
public class PublicSiteProperties {

    private String homeBaseUrl;
    private String blogBaseUrl;
    private String cvBaseUrl;
    private String apiBaseUrl;

    /** 缺失配置时关闭依赖它的入口，不猜部署域名。 */
    public URI base(String code) {
        String value = switch (code) {
            case "home" -> homeBaseUrl;
            case "blog" -> blogBaseUrl;
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

    /** 从明确配置的Origin和最长路径前缀认定站点，同域子站不能伪装成主页。 */
    public void requireSite(String code, String origin, String path) {
        URI source;
        try {
            source = URI.create(origin == null ? "" : origin);
            if (source.getHost() == null || source.getRawUserInfo() != null || source.getRawQuery() != null || source.getRawFragment() != null
                    || !(source.getPath() == null || source.getPath().isEmpty())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new BusinessException(403, "FORBIDDEN", "采集来源不被允许");
        }
        base(code);
        String match = null;
        int longest = -1;
        for (String candidate : List.of("home", "blog", "cv")) {
            String configured = switch (candidate) {
                case "home" -> homeBaseUrl;
                case "blog" -> blogBaseUrl;
                default -> cvBaseUrl;
            };
            if (configured == null || configured.isBlank()) {
                continue;
            }
            URI value = base(candidate);
            String prefix = value.getPath();
            if (value.getScheme().equalsIgnoreCase(source.getScheme()) && value.getRawAuthority()
                    .equalsIgnoreCase(source.getRawAuthority())
                    && (prefix.isEmpty() || path.equals(prefix) || path.startsWith(prefix + "/"))) {
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

    /** 全站统一文章前端地址，采集与Feed使用同一个规则。 */
    public String articleUrl(String slug) {
        return base("blog").toString() + "/articles/" + slug;
    }
}
