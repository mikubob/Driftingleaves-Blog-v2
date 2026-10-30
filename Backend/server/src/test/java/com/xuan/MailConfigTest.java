package com.xuan;

import com.xuan.client.MailClient;
import com.xuan.config.MailConfig;
import com.xuan.exception.BusinessException;
import com.xuan.properties.MailProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

/** 仅替换SMTP传输边界，实际执行应用MailClient装配与错误转换。 */
class MailConfigTest {
    @Test
    void disabledMailRejectsInsteadOfPretendingToSend() {
        var factory = new StaticListableBeanFactory();
        MailClient client = new MailConfig().mailClient(new MailProperties(),factory.getBeanProvider(JavaMailSender.class));
        assertThatThrownBy(() -> client.send("recipient@example.test","主题","正文")).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
    }
    @Test
    void enabledMailActuallyUsesConfiguredTransportAndMasksFailures() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var factory = new StaticListableBeanFactory(Map.of("sender",sender));
        MailProperties properties = configured();
        MailClient client = new MailConfig().mailClient(properties,factory.getBeanProvider(JavaMailSender.class));
        client.send("recipient@example.test","主题","正文");
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@example.test"); assertThat(message.getValue().getTo()).containsExactly("recipient@example.test");
        doThrow(new MailSendException("secret transport detail")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> client.send("recipient@example.test","主题","正文")).isInstanceOfSatisfying(BusinessException.class,
                error -> { assertThat(error.getStatus()).isEqualTo(503); assertThat(error.getMessage()).doesNotContain("secret"); });
    }
    @Test
    void enabledMailRequiresTransportAndFixedCompletionPages() {
        var factory = new StaticListableBeanFactory();
        assertThatThrownBy(() -> new MailConfig().mailClient(configured(),factory.getBeanProvider(JavaMailSender.class))).isInstanceOf(IllegalArgumentException.class);
        MailProperties properties = configured(); properties.setConfirmUrl("https://example.test/confirm?redirect=untrusted");
        assertThatThrownBy(() -> new MailConfig().mailClient(properties,factory.getBeanProvider(JavaMailSender.class))).isInstanceOf(IllegalArgumentException.class);
    }
    /** 所有邮箱与地址都是测试占位，不向外部SMTP发送。 */
    private MailProperties configured() {
        MailProperties properties = new MailProperties(); properties.setEnabled(true); properties.setFrom("sender@example.test");
        properties.setConfirmUrl("https://example.test/confirm"); properties.setUnsubscribeUrl("https://example.test/unsubscribe"); return properties;
    }
}
