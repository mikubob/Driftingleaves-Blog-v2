package com.xuan.config;

import com.xuan.client.MailClient;
import com.xuan.exception.BusinessException;
import com.xuan.properties.MailProperties;
import com.xuan.utils.UrlPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用Boot邮件连接实际发送，默认关闭时请求明确503，不安装空发送器。
 */
@Configuration
public class MailConfig {

    /** SMTP凭据只从外部配置读取，错误消息不包含收件地址、票据或服务器响应。 */
    @Bean
    public MailClient mailClient(MailProperties properties, ObjectProvider<JavaMailSender> senders) {
        if (properties.isEnabled() && (!UrlPolicy.valid(
                properties.getConfirmUrl()) || properties.getConfirmUrl() == null
                || !UrlPolicy.valid(properties.getUnsubscribeUrl()) || properties.getUnsubscribeUrl() == null
                || URI.create(properties.getConfirmUrl()).getRawQuery() != null || URI.create(
                properties.getConfirmUrl()).getRawFragment() != null
                || URI.create(properties.getUnsubscribeUrl()).getRawQuery() != null || URI.create(
                properties.getUnsubscribeUrl()).getRawFragment() != null
                || properties.getFrom() == null || properties.getFrom()
                .isBlank() || senders.getIfAvailable() == null)) {
            throw new IllegalArgumentException("启用邮件须配置SMTP、发件地址与固定完成页");
        }
        return (to, subject, text) -> {
            if (!properties.isEnabled()) {
                throw unavailable();
            }
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.getFrom());
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                senders.getObject().send(message);
            } catch (MailException error) {
                throw unavailable();
            }
        };
    }

    /** 网络发送不在业务事务或请求线程等待中执行，容器关闭时等已接收任务。 */
    @Bean(name = "mailExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService mailExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mail-", 0).factory());
    }

    /** 不回显传输异常。 */
    private static BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "邮件服务暂不可用");
    }
}
