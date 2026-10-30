package com.xuan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 邮件能力默认关闭，启用后必须提供SMTP和固定前端完成页。 */
@Data
@ConfigurationProperties("app.mail")
public class MailProperties {
    private boolean enabled;
    private String from;
    private String confirmUrl;
    private String unsubscribeUrl;
}
