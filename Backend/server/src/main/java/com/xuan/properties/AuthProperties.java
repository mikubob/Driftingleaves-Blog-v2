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

/** 身份基础设施参数；凭据只来自环境或外部配置。 */
@Data
@Validated
@ConfigurationProperties("app.auth")
public class AuthProperties {
    private List<String> allowedOrigins = new ArrayList<>();
    private String frontendCompleteUrl;
    /** 仅这些精确地址可以提供 X-Forwarded-For，不接受任意代理。 */
    private List<String> trustedProxies = new ArrayList<>();
    @NotBlank
    private String redisPrefix = "dl:v2:";
    @Min(4)
    @Max(16)
    private int bcryptStrength = 12;
    @Valid
    private Rate rate = new Rate();
    private Map<String, Provider> providers = new LinkedHashMap<>();

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

    @Data
    public static class Provider {
        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}
