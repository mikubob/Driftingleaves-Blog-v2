package com.xuan.content;

import com.xuan.client.MailClient;
import com.xuan.entity.BlogRssSubscription;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.MessageMapper;
import com.xuan.mapper.SubscriptionMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.MessageRow;
import com.xuan.model.SubscriptionUnsubscribe;
import com.xuan.properties.MailProperties;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.store.SubscriptionTicketStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * 发布/审核提交后才投递邮件，收件人只取当前已确认订阅，不发送私密原文。
 */
@Component
public class SubscriptionNotifications {

    private final SubscriptionMapper subscriptions;
    private final ArticleMapper articles;
    private final MessageMapper messages;
    private final SubscriptionTicketStore tickets;
    private final MailClient mail;
    private final MailProperties properties;
    private final PublicSiteProperties sites;
    private final JsonMapper json;
    private final Clock clock;
    private final ExecutorService executor;

    /** 执行器单独注入，不搬运请求线程、Token或会话上下文。 */
    public SubscriptionNotifications(SubscriptionMapper subscriptions, ArticleMapper articles, MessageMapper messages,
            SubscriptionTicketStore tickets, MailClient mail, MailProperties properties, PublicSiteProperties sites,
            JsonMapper json, Clock clock, @Qualifier("mailExecutor") ExecutorService executor) {
        this.subscriptions = subscriptions;
        this.articles = articles;
        this.messages = messages;
        this.tickets = tickets;
        this.mail = mail;
        this.properties = properties;
        this.sites = sites;
        this.json = json;
        this.clock = clock;
        this.executor = executor;
    }

    /** 只由首次发布入口登记，回滚、下架重发和普通编辑不会触发。 */
    public void afterArticlePublished(long id) {
        afterCommit(() -> article(id));
    }

    /** 只由留言实际变为通过或免审新建时登记，不反复通知相同审核目标。 */
    public void afterMessageApproved(long id) {
        afterCommit(() -> reply(id));
    }

    /** 成功提交后排队；发信失败只记固定运维错误，不回滚已经保存的内容。 */
    private void afterCommit(Runnable task) {
        // shortcut: 通知只在进程内排队，需要跨宕机可靠投递时再引入持久化任务。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void afterCommit() {
                try {
                    executor.submit(() -> {
                        try {
                            task.run();
                        } catch (RuntimeException error) {
                            failed();
                        }
                    });
                } catch (RuntimeException error) {
                    failed();
                }
            }
        });
    }

    /** 扫描已激活收件人，每批100条，发送前再次检查PUBLIC与当前订阅轮次。 */
    private void article(long id) {
        long after = 0;
        while (true) {
            List<BlogRssSubscription> batch = subscriptions.recipients(after);
            if (batch.isEmpty()) {
                return;
            }
            for (BlogRssSubscription candidate : batch) {
                after = candidate.getSubscriptionId();
                ArticleRow row = articles.publicById(id, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
                if (row == null) {
                    return;
                }
                BlogRssSubscription current = subscriptions.confirmed(candidate.getUserId());
                if (current != null && current.getSubscriptionId().equals(candidate.getSubscriptionId())
                        && Objects.equals(current.getEmail(), candidate.getEmail()) && Objects.equals(
                        current.getSubscribeTime(), candidate.getSubscribeTime())) {
                    try {
                        send(current, "新文章发布", row.getTitle() + "\n" + sites.articleUrl(row.getSlug()));
                    } catch (RuntimeException error) {
                        failed();
                    }
                }
            }
        }
    }

    /** 只提醒直接被回复者，不使用登录邮箱，也不在正文里拼入任何留言原文。 */
    private void reply(long id) {
        MessageRow reply = messages.scoped(id, null);
        if (reply == null || reply.getStatus() != 1 || reply.getParentId() == null) {
            return;
        }
        MessageRow parent = messages.scoped(reply.getParentId(), null);
        if (parent == null || parent.getStatus() == 3 || parent.getNotifyReply() != 1 || parent.getUserId()
                .equals(reply.getUserId())) {
            return;
        }
        if (!MessageVisibility.visible(id, MessageVisibility.index(messages.nodes()), parent.getUserId())) {
            return;
        }
        BlogRssSubscription current = subscriptions.confirmed(parent.getUserId());
        if (current != null) {
            send(current, "留言收到新回复", "你的留言收到新回复，请登录查看：\n" + sites.base("blog") + "/messages");
        }
    }

    /** 每封实际发送的邮件创建本轮可幂等使用的退订能力。 */
    private void send(BlogRssSubscription row, String subject, String text) {
        String round = tickets.deliveryRound(row.getSubscriptionId());
        if (round == null) {
            throw new IllegalStateException("订阅邮件轮次缺失");
        }
        String ticket = tickets.unsubscribeTicket(json.writeValueAsString(
                new SubscriptionUnsubscribe(row.getSubscriptionId(), row.getEmail(), row.getVerifiedAt(),
                        row.getSubscribeTime(), round)));
        mail.send(row.getEmail(), subject,
                text + "\n退订：" + properties.getUnsubscribeUrl() + "#unsubscribeTicket=" + ticket);
    }

    /** 不把邮箱、原文、票据或SMTP异常写进运维日志。 */
    private static void failed() {
        System.getLogger(SubscriptionNotifications.class.getName())
                .log(System.Logger.Level.ERROR, "订阅通知发送失败，请检查邮件服务");
    }
}
