package com.xuan.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 邮件配置，默认关闭，启用时必须配置 SMTP 和固定的前端完成页。
 */
@Data
@Validated
@ConfigurationProperties("app.mail")
public class MailProperties {

    private boolean enabled;
    private String from;

    /** 收件人看到的发件人名称，与 SMTP 发件地址分开配置。 */
    private String fromName = "DriftingLeaves";

    private String confirmUrl;
    private String unsubscribeUrl;

    /** 文章与留言通知共用的并发上限，不影响同步验证码和订阅确认邮件。 */
    @Min(1)
    private int notificationConcurrency = 4;
}
