package com.xuan.config;

import com.xuan.client.MailClient;
import com.xuan.exception.BusinessException;
import com.xuan.policy.UrlPolicy;
import com.xuan.properties.MailProperties;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 配置邮件发送，使用 Boot 邮件连接；关闭时请求返回 503。
 */
@Configuration
public class MailConfig {

    /** 创建邮件客户端，SMTP 凭据从外部配置读取，报错时不返回收件地址、票据或服务器响应。 */
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
        return new MailClient() {
            /** 发送纯文本邮件，保留现有调用方式。 */
            @Override
            public void send(String to, String subject, String text) {
                send(to, subject, text, null);
            }

            /** 发送 UTF-8 邮件，HTML 和纯文本作为两种正文供客户端选择。 */
            @Override
            public void send(String to, String subject, String text, String html) {
                if (!properties.isEnabled()) {
                    throw unavailable();
                }
                try {
                    JavaMailSender sender = senders.getObject();
                    MimeMessageHelper message = new MimeMessageHelper(sender.createMimeMessage(),
                            html != null, StandardCharsets.UTF_8.name());
                    message.setFrom(properties.getFrom(), properties.getFromName());
                    message.setTo(to);
                    message.setSubject(subject);
                    if (html == null) {
                        message.setText(text);
                    } else {
                        message.setText(text, html);
                    }
                    sender.send(message.getMimeMessage());
                } catch (MailException | MessagingException | UnsupportedEncodingException error) {
                    throw unavailable();
                }
            }
        };
    }

    /** 配置邮件线程池，异步发送邮件，关闭时等待已接收的任务完成。 */
    @Bean(name = "mailExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService mailExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mail-", 0).factory());
    }

    /** 返回邮件服务不可用错误，不带发送异常详情。 */
    private static BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "邮件服务暂不可用");
    }
}
