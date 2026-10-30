package com.xuan.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

/** 外部 OSS 配置；默认关闭，不通过 toString 暴露密钥，启用时由装配层校验。 */
@Getter
@Setter
@ConfigurationProperties("app.oss")
public class AliOssProperties {
    private boolean enabled;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    /** 可选 STS 凭据；当前由外部配置提供，不自动续期。 */
    private String securityToken;
    /** 所有读写操作限制在专属前缀内，不接受空前缀。 */
    private String keyPrefix = "drifting-leaves-v2/";

    /** 校验启用后的完整配置，错误消息只包含字段职责，不回显凭据或地址。 */
    public void validateEnabled() {
        URI uri;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OSS endpoint 必须是 HTTPS origin");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("OSS endpoint 必须是 HTTPS origin");
        }
        if (region == null || !region.matches("[a-z0-9-]{1,63}")
                || bucket == null || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                || accessKeyId == null || accessKeyId.isBlank()
                || accessKeySecret == null || accessKeySecret.isBlank()) {
            throw new IllegalArgumentException("启用 OSS 必须提供 region、bucket 和外部凭据");
        }
        if (keyPrefix == null || keyPrefix.length() > 200
                || !keyPrefix.matches("(?:[A-Za-z0-9_-]+/)+")) {
            throw new IllegalArgumentException("OSS key-prefix 必须是非空 ASCII 目录前缀");
        }
    }
}
