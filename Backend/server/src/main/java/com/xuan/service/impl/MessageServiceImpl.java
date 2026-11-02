package com.xuan.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.MessageAdminQueryDTO;
import com.xuan.dto.MessageCreateDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.MessageOwnQueryDTO;
import com.xuan.dto.MessagePublicQueryDTO;
import com.xuan.entity.BlogMessage;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.exception.BusinessException;
import com.xuan.location.IpLocationResolver;
import com.xuan.mapper.MessageMapper;
import com.xuan.mapper.SiteConfigMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.MessageNode;
import com.xuan.model.MessageRow;
import com.xuan.model.ResourceVersion;
import com.xuan.notification.SubscriptionNotifier;
import com.xuan.policy.MessageVisibilityPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IMessageService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.MarkdownUtils;
import com.xuan.utils.QueryTimeUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UserAgentUtils;
import com.xuan.vo.ArticlePublicVO;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.MessagePrivateVO;
import com.xuan.vo.MessagePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 留言业务实现，写入时依次锁住操作者、message 配置和全部上级留言，每次等待后重新检查原会话。
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, BlogMessage> implements IMessageService {

    private static final String DELETED = "该留言已删除";
    private final MessageMapper messages;
    private final SiteConfigMapper configs;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final SubscriptionNotifier notifications;
    private final JsonMapper json;
    private final Clock clock;
    private final IpLocationResolver locations;

    /** 分页查询可见留言及直接回复。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<MessagePublicVO> publicMessages(MessagePublicQueryDTO query) {
        Long viewer = sessions.currentToken() == null ? null : requireMember(null);
        Long parent = IdUtils.parse(query.parentId());
        List<MessageNode> nodes = messages.nodes();
        Map<Long, MessageNode> index = MessageVisibilityPolicy.index(nodes);
        if (parent != null && !MessageVisibilityPolicy.visible(parent, index, viewer)) {
            throw missing();
        }
        // shortcut: 目前沿用评论的方式查询全部上级留言，留言过多导致扫描太慢时再改查询方式。
        List<Long> visible = nodes.stream().filter(node -> Objects.equals(parent, node.getParentId()))
                .filter(node -> MessageVisibilityPolicy.visible(node.getMessageId(), index, viewer))
                .map(MessageNode::getMessageId).toList();
        int from = Math.min(visible.size(), (query.page() - 1) * query.pageSize());
        List<Long> ids = visible.subList(from, Math.min(visible.size(), from + query.pageSize()));
        List<MessagePublicVO> rows = CollUtil.isEmpty(ids) ? List.of() : messages.visibleRows(ids).stream()
                .map(row -> publicView(row, viewer)).toList();
        return new PageResult<>(query.page(), query.pageSize(), Integer.toString(visible.size()),
                Long.toString((visible.size() + (long) query.pageSize() - 1) / query.pageSize()), rows);
    }

    /** 发表留言。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MessagePrivateVO create(MessageCreateDTO request, String ip, String userAgent) {
        // 1. 锁住用户和留言开关，每次等待结束后重新检查原会话与权限。
        long actor = requireMember("create");
        users.lockById(actor);
        requireMember("create");
        JsonNode policy = policy(lockConfig());
        requireMember("create");
        requireEnabled(policy);

        // 2. 从根到父留言依次加锁，确认可以回复，并保留全部上级留言的私密限制。
        List<BlogMessage> path = lockPath(request.getParentId(), "create", false);
        requireReplyable(path, actor, false);

        // 3. 作者取当前会话，审核状态取站点配置，不接收客户端指定的身份或状态。
        BlogMessage row = new BlogMessage();
        row.setUserId(actor);
        row.setParentId(request.getParentId());
        row.setContent(request.getContent());
        row.setContentFormat(request.getContentFormat());
        row.setNotifyReply(request.getNotifyReply());
        row.setIsSecret(request.getIsSecret() == 1 || secret(path) ? 1 : 0);
        row.setStatus(policy.get("reviewRequired").asBoolean() ? 0 : 1);
        row.setLocation(locations.getIpLocation(ip));
        row.setUserAgentOs(UserAgentUtils.operatingSystem(userAgent));
        row.setUserAgentBrowser(UserAgentUtils.browser(userAgent));

        // 4. 保存后登记通知，只有事务提交且留言通过审核才发送。
        messages.insert(row);
        if (row.getStatus() == 1) {
            notifications.afterMessageApproved(row.getMessageId());
        }
        return privateView(required(messages.scoped(row.getMessageId(), actor)), actor);
    }

    /** 分页查询本人留言。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<MessagePrivateVO> ownMessages(MessageOwnQueryDTO query) {
        long actor = requireMember(null);
        return PageResult.from(
                messages.messagePage(new Page<>(query.page(), query.pageSize()), actor, query.status(), null, null,
                                null)
                        .convert(row -> privateView(row, actor)));
    }

    /** 根据id查询本人留言及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<MessagePrivateVO> ownDetail(long id) {
        long actor = requireMember(null);
        return profile(required(messages.scoped(id, actor)), actor);
    }

    /** 根据id修改本人留言。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @RateLimit(value = "message-create", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResourceVersion<MessagePrivateVO> edit(long id, MessageEditDTO request, String revision) {
        // 1. 先锁用户和开关，关闭留言后不能继续编辑，但仍保留查询和删除。
        long actor = requireMember("updateOwn");
        users.lockById(actor);
        requireMember("updateOwn");
        JsonNode policy = policy(lockConfig());
        requireMember("updateOwn");
        requireEnabled(policy);

        // 2. 锁住全部上级留言后，重新检查本人范围和版本，删除占位不能恢复原文。
        required(messages.scoped(id, actor));
        lockPath(id, "updateOwn", false);
        MessageRow row = required(messages.scoped(id, requireMember("updateOwn")));
        RevisionUtils.check(revision(row), revision);
        if (row.getStatus() == 3) {
            throw conflict();
        }

        // 3. 只修改可编辑内容，保留父关系和私密性，并重新进入待审状态。
        row.setContent(request.getContent());
        row.setContentFormat(request.getContentFormat());
        row.setNotifyReply(request.getNotifyReply());
        row.setEditedAt(now());
        row.setStatus(0);
        messages.change(row, actor);
        return profile(required(messages.scoped(id, actor)), actor);
    }

    /** 根据id删除本人留言及全部子回复。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteOwn(long id) {
        long actor = requireMember("deleteOwn");
        users.lockById(actor);
        requireMember("deleteOwn");
        lockConfig();
        requireMember("deleteOwn");
        required(messages.scoped(id, actor));
        lockPath(id, "deleteOwn", false);
        required(messages.scoped(id, requireMember("deleteOwn")));
        deleteTree(id, true);
    }

    /** 分页查询后台留言。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<MessagePrivateVO> adminMessages(MessageAdminQueryDTO query) {
        long actor = requireAdmin("query");
        LocalDateTime begin = QueryTimeUtils.parse(query.begin());
        LocalDateTime end = QueryTimeUtils.parse(query.end());
        QueryTimeUtils.requireRange(begin, end);
        return PageResult.from(
                messages.messagePage(new Page<>(query.page(), query.pageSize()), IdUtils.parse(query.userId()),
                                query.status(), query.isSecret(), begin, end)
                        .convert(row -> privateView(row, actor)));
    }

    /** 根据id查询后台留言及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<MessagePrivateVO> adminDetail(long id) {
        long actor = requireAdmin("query");
        return profile(required(messages.scoped(id, null)), actor);
    }

    /** 根据id审核留言。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "review", target = "blog:message", permission = "blog:message:review", targetIdArgument = 0)
    public ResourceVersion<MessagePrivateVO> review(long id, int status, String expected) {
        // 1. 锁住配置和全部上级留言后检查版本，上级隐藏留言未通过时，不能单独让下级回复通过。
        List<BlogMessage> path = lockAdmin(id, "review");
        MessageRow row = required(messages.scoped(id, null));
        RevisionUtils.check(revision(row), expected);
        if (row.getStatus() == 3) {
            throw conflict();
        }
        if (status == 1 && path.subList(0, path.size() - 1).stream()
                .anyMatch(parent -> parent.getStatus() != 1 && parent.getStatus() != 3)) {
            throw conflict();
        }

        // 2. 状态实际变化时才写入，只有本次转为通过才登记提交后的通知。
        boolean approved = row.getStatus() != 1 && status == 1;
        if (row.getStatus() != status) {
            row.setStatus(status);
            messages.change(row, null);
        }
        if (approved) {
            notifications.afterMessageApproved(id);
        }
        return profile(required(messages.scoped(id, null)), sessions.currentUserId());
    }

    /** 在后台回复留言。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "reply", target = "blog:message", permission = "blog:message:review", targetIdArgument = 0)
    @RateLimit(value = "message-reply", limit = 10, dimension = RateLimit.Dimension.USER)
    public MessagePrivateVO replyAdmin(long id, MessageEditDTO request, String ip, String userAgent) {
        // 1. 后台回复也要检查开关和全部上级留言，管理员身份不能放宽私密范围。
        long actor = requireAdmin("review");
        users.lockById(actor);
        requireAdmin("review");
        JsonNode policy = policy(lockConfig());
        requireAdmin("review");
        requireEnabled(policy);
        List<BlogMessage> path = lockPath(id, "review", true);
        requireReplyable(path, actor, true);

        // 2. 作者取实际操作人，保留上级留言的私密限制，后台回复直接设为审核通过。
        BlogMessage row = new BlogMessage();
        row.setUserId(actor);
        row.setParentId(id);
        row.setContent(request.getContent());
        row.setContentFormat(request.getContentFormat());
        row.setNotifyReply(request.getNotifyReply());
        row.setIsSecret(secret(path) ? 1 : 0);
        row.setStatus(1);
        row.setLocation(locations.getIpLocation(ip));
        row.setUserAgentOs(UserAgentUtils.operatingSystem(userAgent));
        row.setUserAgentBrowser(UserAgentUtils.browser(userAgent));

        // 3. 保存回复后登记提交后的通知，不在事务提交前发送邮件。
        messages.insert(row);
        notifications.afterMessageApproved(row.getMessageId());
        return privateView(required(messages.scoped(row.getMessageId(), null)), actor);
    }

    /** 审核或删除留言前，锁住配置和全部上级留言；关闭留言开关后，仍允许管理历史留言。 */
    private List<BlogMessage> lockAdmin(long id, String operation) {
        long actor = requireAdmin(operation);
        users.lockById(actor);
        requireAdmin(operation);
        lockConfig();
        requireAdmin(operation);
        return lockPath(id, operation, true);
    }

    /** 锁住目标留言和全部上级留言，回复关系不可改，从根到目标依次加锁，遇到循环或缺失记录时拒绝操作。 */
    private List<BlogMessage> lockPath(Long id, String operation, boolean admin) {
        if (id == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long next = id;
        while (next != null) {
            if (!seen.add(next)) {
                throw conflict();
            }
            BlogMessage row = messages.selectById(next);
            if (row == null) {
                throw missing();
            }
            ids.add(next);
            next = row.getParentId();
        }
        Collections.reverse(ids);
        List<BlogMessage> path = new ArrayList<>();
        for (long target : ids) {
            BlogMessage row = messages.lockById(target);
            if (admin) {
                requireAdmin(operation);
            } else {
                requireMember(operation);
            }
            if (row == null) {
                throw missing();
            }
            path.add(row);
        }
        return path;
    }

    /** 检查留言能否回复，普通用户必须能看到全部上级留言；后台也不能回复待审、驳回或删除占位的目标。 */
    private void requireReplyable(List<BlogMessage> path, long actor, boolean admin) {
        if (CollUtil.isEmpty(path)) {
            return;
        }
        BlogMessage target = path.getLast();
        if (!admin && !MessageVisibilityPolicy.visible(target.getMessageId(), MessageVisibilityPolicy.index(messages.nodes()),
                actor)) {
            throw missing();
        }
        if (target.getStatus() != 1 || path.stream().anyMatch(row -> row.getStatus() != 1 && row.getStatus() != 3)) {
            throw conflict();
        }
    }

    /** 判断留言是否私密，只要有上级留言是私密的，客户端传 0 也不能改为公开。 */
    private boolean secret(List<BlogMessage> path) {
        return path.stream().anyMatch(row -> row.getIsSecret() == 1);
    }

    /** 读取留言配置，与后台维护使用同一条记录，不另加缓存。 */
    private BlogSiteConfig lockConfig() {
        BlogSiteConfig row = configs.lockByKey("message");
        if (row == null) {
            throw unavailable();
        }
        return row;
    }

    /** 两项布尔值之外都算部署错误，不能用默认值悄悄放开留言。 */
    private JsonNode policy(BlogSiteConfig row) {
        try {
            if (row.getIsPublic() != 0 || row.getConfigValue().getBytes(StandardCharsets.UTF_8).length > 16384) {
                throw unavailable();
            }
            JsonNode node = json.readTree(row.getConfigValue());
            if (!node.isObject() || node.size() != 2 || !node.path("enabled").isBoolean() || !node.path(
                    "reviewRequired").isBoolean()) {
                throw unavailable();
            }
            return node;
        } catch (JacksonException error) {
            throw unavailable();
        }
    }

    /** 关闭只限制新增、编辑和回复。 */
    private void requireEnabled(JsonNode policy) {
        if (!policy.get("enabled").asBoolean()) {
            throw new BusinessException(409, "STATE_CONFLICT", "留言已关闭");
        }
    }

    /** 每个锁等待结束都重查原Token和启用身份。 */
    private long requireMember(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        if (operation != null) {
            authorization.requireScope(actor, "blog:message:" + operation);
        }
        return actor;
    }

    /** 查询、审核、管理删除分别取自己的ALL。 */
    private long requireAdmin(String operation) {
        long actor = requireMember(null);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:message:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
        return actor;
    }

    /** 删除留言及全部子回复，配置锁阻止新增回复；锁住待删留言并重新检查权限后，从子回复向父留言依次删除。 */
    private void deleteTree(long id, boolean own) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (MessageNode node : messages.nodes()) {
            children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getMessageId());
        }
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        for (int index = 0; index < ids.size(); index++) {
            ids.addAll(children.getOrDefault(ids.get(index), List.of()));
        }
        for (long target : ids) {
            messages.lockById(target);
            if (own) {
                requireMember("deleteOwn");
            } else {
                requireAdmin("moderate");
            }
        }
        for (long target : ids.reversed()) {
            messages.deleteById(target);
        }
    }

    /** 计算留言版本，不包含昵称、头像和审计时间。 */
    private String revision(BlogMessage row) {
        return "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(row.getUserId(), row.getParentId(), row.getContent(), row.getContentFormat(),
                        row.getIsSecret(), row.getNotifyReply(), row.getStatus(), row.getEditedAt()))) + "\"";
    }

    /** 整理本人留言资料，复用公开响应中的已处理字段，额外返回回复通知设置。 */
    private MessagePrivateVO privateView(MessageRow row, Long viewer) {
        MessagePublicVO value = publicView(row, viewer);
        return new MessagePrivateVO(value.messageId(), value.parentId(), value.author(), value.content(),
                value.htmlContent(), value.contentFormat(),
                value.isSecret(), value.status(), value.editedAt(), value.isOwn(), value.createTime(),
                value.updateTime(), row.getStatus() == 3 ? 0 : row.getNotifyReply(), value.location(),
                value.userAgentOs(), value.userAgentBrowser());
    }

    /** 删除占位永不返回旧原文，纯文本转义并保留换行。 */
    private MessagePublicVO publicView(MessageRow row, Long viewer) {
        boolean deleted = row.getStatus() == 3;
        String content = deleted ? DELETED : row.getContent();
        int format = deleted ? 0 : row.getContentFormat();
        String html = format == 1 ? MarkdownUtils.toHtml(content) : "<p>" + content.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>\n") + "</p>";
        return new MessagePublicVO(row.getMessageId(), row.getParentId(),
                deleted ? null : new ArticlePublicVO.Author(row.getUserId(), row.getAuthorNickname(),
                        row.getAuthorAvatar()),
                content, html, format, row.getIsSecret(), row.getStatus(), row.getEditedAt(),
                Objects.equals(viewer, row.getUserId()), row.getCreateTime(), row.getUpdateTime(),
                deleted ? null : row.getLocation(), deleted ? null : row.getUserAgentOs(),
                deleted ? null : row.getUserAgentBrowser());
    }

    /** 返回当前留言资料及对应版本。 */
    private ResourceVersion<MessagePrivateVO> profile(MessageRow row, Long viewer) {
        return new ResourceVersion<>(privateView(row, viewer), revision(row));
    }

    /** 检查留言是否存在，必须先由 SQL 限制归属范围。 */
    private MessageRow required(MessageRow row) {
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /** UTC毫秒时间与数据库精度一致。 */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    /** 不向前台区分私密、隐藏或不存在。 */
    private BusinessException missing() {
        return new BusinessException(404, "NOT_FOUND", "留言不存在");
    }

    /** 状态不允许当前操作。 */
    private BusinessException conflict() {
        return new BusinessException(409, "STATE_CONFLICT", "留言状态不允许此操作");
    }

    /** 不回显配置值。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "留言配置不可用");
    }

    /** 批量审核留言，同批通过的上级留言可供下级回复通过检查；未选中的上级留言不可见时，整批拒绝通过。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-review", target = "blog:message", permission = "blog:message:review")
    public BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status) {
        var sorted = BatchUtils.sorted(items);
        Set<Long> selected = new HashSet<>();
        for (var item : items) {
            selected.add(IdUtils.parse(item.id()));
        }
        Map<Long, MessageRow> rows = new HashMap<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            List<BlogMessage> path = lockAdmin(id, "review");
            MessageRow row = required(messages.scoped(id, null));
            RevisionUtils.check(revision(row), item.revision());
            if (row.getStatus() == 3) {
                throw conflict();
            }
            if (status == 1) {
                for (BlogMessage parent : path.subList(0, path.size() - 1)) {
                    if (parent.getStatus() != 1 && parent.getStatus() != 3 && !selected.contains(parent.getMessageId())) {
                        throw conflict();
                    }
                }
            }
            rows.put(id, row);
        }
        Map<Long, String> versions = new HashMap<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            MessageRow row = rows.get(id);
            boolean approved = row.getStatus() != 1 && status == 1;
            if (row.getStatus() != status) {
                row.setStatus(status);
                messages.change(row, null);
            }
            if (approved) {
                notifications.afterMessageApproved(id);
            }
            versions.put(id, revision(required(messages.scoped(id, null))));
        }
        return BatchUtils.versions(items, versions);
    }

    /** 批量删除留言，先检查所有选中的记录，再删除这些留言及其子回复；保留未选中的上级和同级留言。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:message", permission = "blog:message:moderate")
    public void deleteBatch(List<String> ids) {
        var sorted = BatchUtils.ids(ids);
        for (long id : sorted) {
            lockAdmin(id, "moderate");
            required(messages.scoped(id, null));
        }
        for (long id : sorted) {
            if (messages.selectById(id) != null) {
                deleteTree(id, false);
            }
        }
    }

}
