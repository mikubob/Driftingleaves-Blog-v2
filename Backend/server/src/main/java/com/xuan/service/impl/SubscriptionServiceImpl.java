package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.batch.BatchInputs;
import com.xuan.client.MailClient;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.SubscriptionQueryDTO;
import com.xuan.dto.SubscriptionWriteDTO;
import com.xuan.entity.BlogRssSubscription;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.SubscriptionMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.model.SubscriptionConfirmation;
import com.xuan.model.SubscriptionUnsubscribe;
import com.xuan.properties.MailProperties;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ISubscriptionService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.SubscriptionTicketStore;
import com.xuan.utils.IdUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.SubscriptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 邮箱唯一归属由数据库保证，Redis票据与MySQL之间没有共同事务。
 */
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl extends ServiceImpl<SubscriptionMapper, BlogRssSubscription> implements ISubscriptionService {

    private final SubscriptionMapper subscriptions;
    private final UserMapper users;
    private final IUserService identities;
    private final AuthorizationProvider authorization;
    private final SessionManager sessions;
    private final SubscriptionTicketStore tickets;
    private final MailClient mail;
    private final MailProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    private final PlatformTransactionManager transactions;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SubscriptionVO own() {
        return view(subscriptions.byUser(requireMember(null)));
    }

    /** {@inheritDoc} */
    @Override
    public SubscriptionVO request(SubscriptionWriteDTO request) {
        // 1. 在事务里依次锁住用户与订阅，等待结束后重新检查原会话和订阅权限。
        long actor = requireMember("subscribe");
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Application application = transaction.execute(status -> {
            users.lockById(actor);
            requireMember("subscribe");
            BlogRssSubscription row = subscriptions.lockByUser(actor);
            requireMember("subscribe");
            if (row != null && row.getStatus() == 1 && row.getEmail().equals(request.email())) {
                return new Application(row, null);
            }

            // 2. 更新待确认记录并签发新一轮票据，不能把账号联系邮箱当作已确认邮箱。
            boolean create = row == null;
            if (create) {
                row = new BlogRssSubscription();
                row.setUserId(actor);
            }
            row.setEmail(request.email());
            row.setStatus(0);
            row.setVerifiedAt(null);
            row.setSubscribeTime(null);
            row.setUnsubscribeTime(null);
            if (create) {
                subscriptions.insert(row);
            } else {
                subscriptions.replace(row);
            }
            String ticket = TokenUtils.random();
            String round = TokenUtils.random();
            tickets.newDeliveryRound(row.getSubscriptionId());
            tickets.issue(ticket, actor, round, json.writeValueAsString(
                    new SubscriptionConfirmation(actor, row.getSubscriptionId(), row.getEmail(), round)));
            return new Application(subscriptions.selectById(row.getSubscriptionId()), ticket);
        });

        // 3. 待确认记录提交后再发邮件，SMTP 失败返回 503，用户可以重新申请确认信。
        if (application.ticket() != null) {
            mail.send(application.row().getEmail(), "确认邮件订阅", "请登录发起申请的本站账号，并在固定完成页确认：\n"
                    + properties.getConfirmUrl() + "#confirmTicket=" + application.ticket() + "\n链接24小时内有效。未申请可忽略。");
        }
        return view(application.row());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubscriptionVO confirm(String ticket) {
        // 1. 锁住当前用户及其订阅，锁等待后再次核对原会话。
        long actor = requireMember("subscribe");
        users.lockById(actor);
        requireMember("subscribe");
        BlogRssSubscription row = subscriptions.lockByUser(actor);
        requireMember("subscribe");

        // 2. 票据必须匹配当前用户、邮箱和申请轮次，验证后只能消费一次。
        String raw = tickets.read("confirm", ticket);
        SubscriptionConfirmation value = confirmation(raw);
        if (row == null || row.getStatus() != 0 || value.userId() != actor || value.subscriptionId() != row.getSubscriptionId()
                || !value.email().equals(row.getEmail())) {
            throw SubscriptionTicketStore.invalid();
        }
        tickets.consume(ticket, actor, value.round(), raw);

        // 3. 确认后激活订阅并更新投递轮次，旧退订票据不能影响本轮订阅。
        row.setStatus(1);
        row.setVerifiedAt(now());
        row.setSubscribeTime(now());
        row.setUnsubscribeTime(null);
        tickets.newDeliveryRound(row.getSubscriptionId());
        subscriptions.replace(row);
        return view(subscriptions.selectById(row.getSubscriptionId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelOwn() {
        long actor = requireMember("unsubscribe");
        users.lockById(actor);
        requireMember("unsubscribe");
        BlogRssSubscription row = subscriptions.lockByUser(actor);
        requireMember("unsubscribe");
        if (row != null) {
            cancel(row);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelTicket(String ticket) {
        SubscriptionUnsubscribe value = unsubscribe(tickets.read("unsubscribe", ticket));
        BlogRssSubscription located = subscriptions.selectById(value.subscriptionId());
        if (located == null) {
            throw SubscriptionTicketStore.invalid();
        }
        users.lockById(located.getUserId());
        BlogRssSubscription row = subscriptions.lockById(value.subscriptionId());
        if (row == null || !Objects.equals(row.getEmail(), value.email()) || !Objects.equals(row.getVerifiedAt(),
                value.verifiedAt())
                || !Objects.equals(row.getSubscribeTime(), value.subscribeTime()) || value.round() == null
                || !Objects.equals(tickets.deliveryRound(value.subscriptionId()), value.round())
                || !List.of(1, 2).contains(row.getStatus())) {
            throw SubscriptionTicketStore.invalid();
        }
        cancel(row);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogRssSubscription> listSubscriptions(SubscriptionQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(subscriptions.subscriptionPage(new Page<>(query.page(), query.pageSize()), query,
                IdUtils.parse(query.userId())));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<BlogRssSubscription> detail(long id) {
        requireAdmin("query");
        return profile(required(subscriptions.selectById(id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "status", target = "blog:rss", permission = "blog:rss:update", targetIdArgument = 0)
    public ResourceVersion<BlogRssSubscription> changeStatus(long id, int status, String revision) {
        BlogRssSubscription row = lockAdmin(id, "update");
        RevisionUtils.check(profile(row).revision(), revision);
        if (status == 1 && (row.getVerifiedAt() == null || users.selectById(row.getUserId()).getStatus() != 1)) {
            throw new BusinessException(409, "STATE_CONFLICT", "当前邮箱尚未验证或用户已停用");
        }
        if (row.getStatus() != status) {
            if (status == 2) {
                cancel(row);
            } else {
                row.setStatus(1);
                row.setSubscribeTime(now());
                row.setUnsubscribeTime(null);
                tickets.newDeliveryRound(id);
                subscriptions.replace(row);
            }
        }
        return profile(subscriptions.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:rss", permission = "blog:rss:delete", targetIdArgument = 0)
    public void delete(long id, String revision) {
        deleteBatch(List.of(new BatchItemDTO(Long.toString(id), revision)));
    }

    /** 后台先查权限，再按ID锁操作者和所属用户，每个等待阶段都重查身份和ALL。 */
    private BlogRssSubscription lockAdmin(long id, String operation) {
        requireAdmin(operation);
        BlogRssSubscription located = required(subscriptions.selectById(id));
        Set<Long> ids = new TreeSet<>();
        ids.add(sessions.currentUserId());
        ids.add(located.getUserId());
        for (long user : ids) {
            users.lockById(user);
            requireAdmin(operation);
        }
        BlogRssSubscription row = subscriptions.lockById(id);
        requireAdmin(operation);
        return required(row);
    }

    /** 已退订不重写时间，旧链接重试仍保持幂等。 */
    private void cancel(BlogRssSubscription row) {
        if (row.getStatus() != 2) {
            row.setStatus(2);
            row.setUnsubscribeTime(now());
            subscriptions.replace(row);
        }
    }

    /** 本人查询不额外要求后台权限，写入仍检查独立前台权限。 */
    private long requireMember(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        if (operation != null) {
            authorization.requireScope(actor, "blog:rss:" + operation);
        }
        return actor;
    }

    /** ALL只从授予当前操作的角色计算。 */
    private void requireAdmin(String operation) {
        long actor = requireMember(null);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:rss:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 确认载荷格式错误也按票据无效处理，不回显原JSON。 */
    private SubscriptionConfirmation confirmation(String raw) {
        try {
            return json.readValue(raw, SubscriptionConfirmation.class);
        } catch (JacksonException error) {
            throw SubscriptionTicketStore.invalid();
        }
    }

    /** 退订载荷只来自独立Redis命名空间。 */
    private SubscriptionUnsubscribe unsubscribe(String raw) {
        try {
            return json.readValue(raw, SubscriptionUnsubscribe.class);
        } catch (JacksonException error) {
            throw SubscriptionTicketStore.invalid();
        }
    }

    /** 不能向本人暴露其他用户ID或票据。 */
    private SubscriptionVO view(BlogRssSubscription row) {
        return row == null ? null : new SubscriptionVO(row.getSubscriptionId(), row.getEmail(), row.getStatus(),
                row.getVerifiedAt(),
                row.getSubscribeTime(), row.getUnsubscribeTime(), row.getCreateTime(), row.getUpdateTime());
    }

    /** 权限检查后才报告不存在。 */
    private BlogRssSubscription required(BlogRssSubscription row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "订阅不存在");
        }
        return row;
    }

    /** 时间和状态都是本业务版本，排除审计时间。 */
    private ResourceVersion<BlogRssSubscription> profile(BlogRssSubscription row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(
                json.writeValueAsString(Arrays.asList(row.getUserId(), row.getEmail(), row.getStatus(),
                        row.getVerifiedAt(), row.getSubscribeTime(), row.getUnsubscribeTime()))) + "\"");
    }

    /** 数据库UTC精度为毫秒，票据保存相同精度供比较。 */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * 仅供请求方法把提交结果带到真实发信阶段，不对外序列化票据。
     */
    private record Application(BlogRssSubscription row, String ticket) {

    }

    /** 先锁定整批所属用户并完成校验，之后才修改订阅和投递轮次。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:rss", permission = "blog:rss:delete")
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchInputs.sorted(items);
        requireAdmin("delete");
        Set<Long> userIds = new TreeSet<>();
        userIds.add(sessions.currentUserId());
        for (var item : sorted) {
            userIds.add(required(subscriptions.selectById(IdUtils.parse(item.id()))).getUserId());
        }
        for (long id : userIds) {
            users.lockById(id);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            BlogRssSubscription row = lockAdmin(id, "delete");
            RevisionUtils.check(profile(row).revision(), revision);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            subscriptions.deleteById(id);
        }
    }

    /** 先锁定整批所属用户并完成校验，之后才修改订阅和投递轮次。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-status", target = "blog:rss", permission = "blog:rss:update")
    public BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status) {
        var sorted = BatchInputs.sorted(items);
        requireAdmin("update");
        Set<Long> userIds = new TreeSet<>();
        userIds.add(sessions.currentUserId());
        for (var item : sorted) {
            userIds.add(required(subscriptions.selectById(IdUtils.parse(item.id()))).getUserId());
        }
        for (long id : userIds) {
            users.lockById(id);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            BlogRssSubscription row = lockAdmin(id, "update");
            RevisionUtils.check(profile(row).revision(), revision);
            if (status == 1 && (row.getVerifiedAt() == null || users.selectById(row.getUserId()).getStatus() != 1)) {
                throw new BusinessException(409, "STATE_CONFLICT", "当前邮箱尚未验证或用户已停用");
            }
        }
        Map<Long, String> versions = new HashMap<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            versions.put(id, changeStatus(id, status, item.revision()).revision());
        }
        return BatchInputs.versions(items, versions);
    }

}
