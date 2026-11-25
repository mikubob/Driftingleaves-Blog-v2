package com.xuan.config;

import com.xuan.client.MailClient;
import com.xuan.constant.ErrorCode;
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

    /**
     * 创建邮件客户端，SMTP 凭据从外部配置读取，报错时不返回收件地址、票据或服务器响应。
     */
    @Bean
    public MailClient mailClient(MailProperties properties, ObjectProvider<JavaMailSender> senders) {
        // 1. 启用邮件时在启动阶段校验 SMTP、发件地址及固定确认/退订页，防止运行期静默失败。
        if (properties.isEnabled()) {
            validateEnabledMail(properties, senders);
        }

        // 2. 返回封装后的客户端，统一屏蔽底层异常细节。
        return new MailClient() {
            /**
             * 发送纯文本邮件，保留现有调用方式。
             */
            @Override
            public void send(String to, String subject, String text) {
                send(to, subject, text, null);
            }

            /**
             * 发送 UTF-8 邮件，HTML 和纯文本作为两种正文供客户端选择。
             */
            @Override
            public void send(String to, String subject, String text, String html) {
                if (!properties.isEnabled()) {
                    throw unavailable();
                }
                try {
                    JavaMailSender sender = senders.getObject();
                    MimeMessageHelper message = new MimeMessageHelper(
                            sender.createMimeMessage(), html != null, StandardCharsets.UTF_8.name());
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

    /**
     * 配置邮件线程池，异步发送邮件，关闭时等待已接收的任务完成。
     */
    @Bean(name = "mailExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService mailExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mail-", 0).factory());
    }

    /**
     * 校验启用邮件时的必填配置。
     */
    private static void validateEnabledMail(MailProperties properties, ObjectProvider<JavaMailSender> senders) {
        boolean valid = isValidFixedPageUrl(properties.getConfirmUrl())
                && isValidFixedPageUrl(properties.getUnsubscribeUrl())
                && properties.getFrom() != null && !properties.getFrom().isBlank()
                && senders.getIfAvailable() != null;
        if (!valid) {
            throw new IllegalArgumentException("启用邮件须配置SMTP、发件地址与固定完成页");
        }
    }

    /**
     * 检查确认页/退订页地址是否合法且不带 query 或 fragment。
     */
    private static boolean isValidFixedPageUrl(String url) {
        if (url == null || !UrlPolicy.valid(url)) {
            return false;
        }
        URI uri = URI.create(url);
        return uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    /**
     * 返回邮件服务不可用错误，不带发送异常详情。
     */
    private static BusinessException unavailable() {
        return new BusinessException(503, ErrorCode.SERVICE_UNAVAILABLE, "邮件服务暂不可用");
    }
}
