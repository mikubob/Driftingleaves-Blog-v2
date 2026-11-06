package com.xuan.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录配置，由应用加载，凭据从环境或外部配置读取。
 */
@Data
@Validated
@ConfigurationProperties("app.auth")
public class AuthProperties {

    /** 兼容旧站绝对附件地址，不用于 HTTP 或 WebSocket 跨域。 */
    private List<String> allowedOrigins = new ArrayList<>();
    /** 从右往左跳过这些明确配置的代理，不能直接相信转发头里的最左地址。 */
    private List<String> trustedProxies = new ArrayList<>();

    @NotBlank
    private String redisPrefix = "dl:v2:";

    @Min(4)
    @Max(16)
    private int bcryptStrength = 12;

    @Valid
    private Rate rate = new Rate();
    private Map<String, Provider> providers = new LinkedHashMap<>();

    /** 认证限流配置，按时间窗口限制各类请求。 */
    @Data
    public static class Rate {

        @Min(1)
        private int windowSeconds = 60;

        @Min(1)
        private int loginFailures = 10;

        @Min(1)
        private int register = 5;

        @Min(1)
        private int oauth = 20;
    }

    /** 第三方登录平台配置，只有启用的平台才参与登录。 */
    @Data
    public static class Provider {

        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}
