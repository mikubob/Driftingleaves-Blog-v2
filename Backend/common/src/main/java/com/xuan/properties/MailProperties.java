package com.xuan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件配置，默认关闭，启用时必须配置 SMTP 和固定的前端完成页。
 */
@Data
@ConfigurationProperties("app.mail")
public class MailProperties {

    private boolean enabled;
    private String from;
    private String confirmUrl;
    private String unsubscribeUrl;
}
