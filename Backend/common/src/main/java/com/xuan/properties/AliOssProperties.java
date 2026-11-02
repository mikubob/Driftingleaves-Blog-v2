package com.xuan.properties;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * OSS 配置，开关从环境读取，启用时检查配置，不生成含密钥的 toString。
 */
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
    /** STS 凭据从外部配置读取，目前不会自动续期。 */
    private String securityToken;
    /** 读写都限制在这个专属前缀内，前缀不能留空。 */
    private String keyPrefix = "drifting-leaves-v2/";

    /** 检查 OSS 配置是否完整，仅在启用时校验，报错不包含凭据或地址。 */
    public void validateEnabled() {
        URI uri;
        try {
            uri = URI.create(StrUtil.nullToEmpty(endpoint));
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
                || StrUtil.nullToEmpty(accessKeyId).isBlank()
                || StrUtil.nullToEmpty(accessKeySecret).isBlank()) {
            throw new IllegalArgumentException("启用 OSS 必须提供 region、bucket 和外部凭据");
        }
        if (keyPrefix == null || keyPrefix.length() > 200
                || !keyPrefix.matches("(?:[A-Za-z0-9_-]+/)+")) {
            throw new IllegalArgumentException("OSS key-prefix 必须是非空 ASCII 目录前缀");
        }
    }
}
