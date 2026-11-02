package com.xuan.notification;

import com.xuan.client.MailClient;
import com.xuan.entity.BlogRssSubscription;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.MessageMapper;
import com.xuan.mapper.SubscriptionMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.MessageRow;
import com.xuan.model.SubscriptionUnsubscribe;
import com.xuan.policy.MessageVisibilityPolicy;
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
 * 发送文章发布和留言回复通知，事务提交后才发给已确认的订阅，不发送私密原文。
 */
@Component
public class SubscriptionNotifier {

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

    /** 初始化邮件通知，使用专用线程池，不传递请求线程、Token 或会话信息。 */
    public SubscriptionNotifier(SubscriptionMapper subscriptions, ArticleMapper articles, MessageMapper messages,
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

    /** 登记新文章通知，仅首次发布时调用，回滚、重新上架和普通编辑不触发。 */
    public void afterArticlePublished(long id) {
        afterCommit(() -> article(id));
    }

    /** 登记留言回复通知，仅审核实际通过或免审创建时调用，不重复通知相同审核结果。 */
    public void afterMessageApproved(long id) {
        afterCommit(() -> reply(id));
    }

    /** 事务提交后发送邮件，发送失败只记录固定错误，不回滚已保存的内容。 */
    private void afterCommit(Runnable task) {
        // shortcut: 通知只在进程内排队，需要跨宕机可靠投递时再引入持久化任务。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交后，将邮件任务交给线程池。
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

    /** 发送新文章通知，每批读取 100 个已激活订阅，发送前重新检查文章公开状态和当前订阅轮次。 */
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

    /** 发送留言回复通知，只提醒直接被回复的人，使用已确认的订阅邮箱，不包含留言原文。 */
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
        if (!MessageVisibilityPolicy.visible(id, MessageVisibilityPolicy.index(messages.nodes()), parent.getUserId())) {
            return;
        }
        BlogRssSubscription current = subscriptions.confirmed(parent.getUserId());
        if (current != null) {
            send(current, "留言收到新回复", "你的留言收到新回复，请登录查看：\n" + sites.base("blog") + "/messages");
        }
    }

    /** 发送订阅邮件，并生成本轮退订票据，重复退订也返回成功。 */
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

    /** 记录发信失败，不保存邮箱、原文、票据或 SMTP 异常详情。 */
    private static void failed() {
        System.getLogger(SubscriptionNotifier.class.getName())
                .log(System.Logger.Level.ERROR, "订阅通知发送失败，请检查邮件服务");
    }
}
