package com.xuan.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置，设置根目录和本站公开来源，不自动创建目录或开放静态资源。
 */
@Data
@Validated
@ConfigurationProperties("app.files")
public class FileProperties {

    @NotNull
    private Path localRoot = Path.of("uploads");
    /** 同源时可以复用 allowed-origins，API 独立部署时在这里补充，不能相信请求 Host。 */
    @NotNull
    private List<String> publicOrigins = new ArrayList<>();

    /** 检查公开来源配置，必须是明确的 HTTP(S) 来源，不能带路径或凭据。 */
    @AssertTrue(message = "files.public-origins必须是具体HTTP(S) origin")
    public boolean isOriginsValid() {
        if (publicOrigins == null) {
            return false;
        }
        for (String value : publicOrigins) {
            try {
                URI uri = URI.create(value);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                        || uri.getRawFragment() != null || !uri.getPath().isEmpty()) {
                    return false;
                }
            } catch (IllegalArgumentException | NullPointerException exception) {
                return false;
            }
        }
        return true;
    }
}
