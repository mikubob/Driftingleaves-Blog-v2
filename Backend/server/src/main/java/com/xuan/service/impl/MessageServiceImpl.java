package com.xuan.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.annotation.PublicCacheRead;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
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
import com.xuan.properties.AuthProperties;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IMessageService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.DiscussionLocationUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.IpUtils;
import com.xuan.utils.MarkdownUtils;
import com.xuan.utils.QueryTimeUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UserAgentUtils;
import com.xuan.vo.ArticlePublicVO;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.DiscussionConversationVO;
import com.xuan.vo.DiscussionReplyTargetVO;
import com.xuan.vo.MessagePrivateVO;
import com.xuan.vo.MessagePublicVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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

    private static final MessagePublicQueryDTO ANONYMOUS_HOME = MessagePublicQueryDTO.builder()
            .page(1)
            .pageSize(10)
            .parentId(null)
            .build();

    private final MessageMapper messages;
    private final SiteConfigMapper configs;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final SubscriptionNotifier notifications;
    private final JsonMapper json;
    private final IpLocationResolver locations;
    private final CaptchaStore captchaStore;
    private final AuthProperties authProperties;
    private final ObjectProvider<IMessageService> self;

    /**
     * 校验算术验证码并发表留言，先在事务外核验验证码与客户端信息，再调用事务方法落库。
     *
     * @param request 留言创建参数
     * @param httpRequest 当前 HTTP 请求
     * @return 新建留言视图
     */
    @Override
    public MessagePrivateVO createWithCaptcha(MessageCreateDTO request, HttpServletRequest httpRequest) {
        captchaStore.verifyArithmetic(httpRequest.getHeader("X-Captcha-Id"), httpRequest.getHeader("X-Captcha-Code"));
        String clientIp = IpUtils.clientIp(httpRequest, authProperties.getTrustedProxies());
        String userAgent = httpRequest.getHeader("User-Agent");
        return self.getObject().create(request, clientIp, userAgent);
    }

    /**
     * 在后台回复留言，先解析可信客户端 IP 与 User-Agent，再调用事务方法落库。
     *
     * @param id 目标留言 ID
     * @param request 回复内容参数
     * @param httpRequest 当前 HTTP 请求
     * @return 新建回复视图
     */
    @Override
    public MessagePrivateVO replyAdmin(long id, MessageEditDTO request, HttpServletRequest httpRequest) {
        String clientIp = IpUtils.clientIp(httpRequest, authProperties.getTrustedProxies());
        String userAgent = httpRequest.getHeader("User-Agent");
        return self.getObject().replyAdmin(id, request, clientIp, userAgent);
    }

    /**
     * 分页查询可见根留言或整组回复。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PublicCacheRead("messages")
    public PageResult<MessagePublicVO> publicMessages(MessagePublicQueryDTO query) {
        Long viewer = sessions.currentToken() == null ? null : requireMember(null);
        if (viewer == null && ANONYMOUS_HOME.equals(query)) {
            return self.getObject().cachedAnonymousHome();
        }
        return loadPublicMessages(query, viewer);
    }

    /**
     * 只用匿名身份查询首页，避免把当前用户可见的留言写入公共缓存。
     */
    @Override
    @Cacheable(cacheNames = "messages", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)")
    public PageResult<MessagePublicVO> cachedAnonymousHome() {
        return loadPublicMessages(ANONYMOUS_HOME, null);
    }

    /**
     * 按读者身份筛选留言及回复，再分页组装结果。
     */
    private PageResult<MessagePublicVO> loadPublicMessages(MessagePublicQueryDTO query, Long viewer) {
        // 1. 加载全部留言节点并建立索引，校验目标父留言对当前读者可见。
        Long parent = IdUtils.parse(query.getParentId());
        List<MessageNode> nodes = messages.nodes();
        Map<Long, MessageNode> index = MessageVisibilityPolicy.index(nodes);
        if (parent != null && !MessageVisibilityPolicy.visible(parent, index, viewer)) {
            throw missing();
        }

        // 2. 构建可见留言树，计算可回复集合并自底向上累计可见回复数。
        Map<Long, List<Long>> children = new HashMap<>();
        for (MessageNode node : nodes) {
            if (MessageVisibilityPolicy.visible(node.getMessageId(), index, viewer)) {
                children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getMessageId());
            }
        }
        List<Long> ordered = new ArrayList<>(children.getOrDefault(null, List.of()));
        Set<Long> replyable = new HashSet<>();
        for (int position = 0; position < ordered.size(); position++) {
            MessageNode node = index.get(ordered.get(position));
            if (node.getStatus() == 1 && (node.getParentId() == null || replyable.contains(node.getParentId()))) {
                replyable.add(node.getMessageId());
            }
            ordered.addAll(children.getOrDefault(node.getMessageId(), List.of()));
        }
        Map<Long, Long> replyCounts = new HashMap<>();
        for (int position = ordered.size() - 1; position >= 0; position--) {
            MessageNode node = index.get(ordered.get(position));
            if (node.getParentId() != null) {
                replyCounts.merge(node.getParentId(), replyCounts.getOrDefault(node.getMessageId(), 0L) + 1, Long::sum);
            }
        }

        // 3. 切出当前页留言 ID，批量查询详情后组装分页结果。
        List<Long> selected = new ArrayList<>(children.getOrDefault(parent, List.of()));
        if (parent != null) {
            for (int position = 0; position < selected.size(); position++) {
                selected.addAll(children.getOrDefault(selected.get(position), List.of()));
            }
        }
        Set<Long> selectedIds = new HashSet<>(selected);
        List<Long> visible = nodes.stream().map(MessageNode::getMessageId).filter(selectedIds::contains).toList();
        int from = Math.min(visible.size(), (query.getPage() - 1) * query.getPageSize());
        List<Long> ids = visible.subList(from, Math.min(visible.size(), from + query.getPageSize()));
        List<MessagePublicVO> rows = CollUtil.isEmpty(ids) ? List.of() : messages.visibleRows(ids).stream()
                .map(row -> {
                    row.setReplyCount(replyCounts.getOrDefault(row.getMessageId(), 0L));
                    row.setReplyable(replyable.contains(row.getMessageId()));
                    return publicView(row, viewer);
                }).toList();
        return new PageResult<>(query.getPage(), query.getPageSize(), Integer.toString(visible.size()),
                Long.toString((visible.size() + (long) query.getPageSize() - 1) / query.getPageSize()), rows);
    }

    /**
     * 发表留言。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "messages", allEntries = true)
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

        // 3. 作者取当前会话，管理员免审，其余按站点配置决定审核状态。
        BlogMessage row = BlogMessage.builder()
                .userId(actor)
                .parentId(request.getParentId())
                .content(request.getContent())
                .contentFormat(request.getContentFormat())
                .notifyReply(request.getNotifyReply())
                .isSecret(request.getIsSecret() == 1 || secret(path) ? 1 : 0)
                .build();
        boolean reviewRequired = policy.get("reviewRequired").asBoolean()
                && !authorization.getRoleList(actor, null).contains("admin");
        row.setStatus(reviewRequired ? 0 : 1);
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

    /**
     * 分页查询本人留言及其前台跳转位置。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<MessagePrivateVO> ownMessages(MessageOwnQueryDTO query) {
        long actor = requireMember(null);
        var result = messages.messagePage(new Page<>(query.getPage(), query.getPageSize()), actor, query.getStatus(),
                null, null, null);
        if (result.getRecords().isEmpty()) {
            return PageResult.from(result.convert(row -> privateView(row, actor)));
        }
        List<MessageNode> nodes = messages.nodes();
        Map<Long, MessageNode> index = MessageVisibilityPolicy.index(nodes);
        List<Long> roots = nodes.stream()
                .filter(node -> node.getParentId() == null
                        && MessageVisibilityPolicy.visible(node.getMessageId(), index, actor))
                .map(MessageNode::getMessageId)
                .toList();
        Map<Long, Integer> pages = DiscussionLocationUtils.rootPages(roots);
        return PageResult.from(result.convert(row -> {
            Long root = DiscussionLocationUtils.rootId(row.getMessageId(), index, MessageNode::getParentId);
            Integer page = null;
            if (root != null && row.getStatus() == 1
                    && MessageVisibilityPolicy.visible(row.getMessageId(), index, actor)) {
                page = pages.get(root);
            }
            return privateView(row, actor, row.getParentId() == null ? null : root, page);
        }));
    }

    /**
     * 根据id查询本人留言及版本。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<MessagePrivateVO> ownDetail(long id) {
        long actor = requireMember(null);
        return profile(required(messages.scoped(id, actor)), actor);
    }

    /**
     * 根据id删除本人留言及全部子回复。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "messages", allEntries = true)
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

    /**
     * 分页查询后台留言或一层回复，保留ALL及私密查询要求。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<MessagePrivateVO> adminMessages(MessageAdminQueryDTO query) {
        long actor = requireAdmin("query");
        Long parent;
        if ("0".equals(query.getParentId())) {
            parent = 0L;
        } else {
            parent = IdUtils.parse(query.getParentId());
        }
        if (parent != null && parent != 0L) {
            required(messages.scoped(parent, null));
        }
        LocalDateTime begin = QueryTimeUtils.parse(query.getBegin());
        LocalDateTime end = QueryTimeUtils.parse(query.getEnd());
        QueryTimeUtils.requireRange(begin, end);
        return PageResult.from(
                messages.adminPage(new Page<>(query.getPage(), query.getPageSize()), IdUtils.parse(query.getUserId()),
                                query.getStatus(), query.getIsSecret(), begin, end, parent)
                        .convert(row -> privateView(row, actor)));
    }

    /**
     * 查询主记录及整组回复，只读取当前页正文；未传页码时定位选中的记录。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DiscussionConversationVO<MessagePrivateVO> conversation(long id, DiscussionConversationQueryDTO query) {
        long actor = requireAdmin("query");
        required(messages.scoped(id, null));
        List<MessageNode> nodes = messages.nodes();
        var page = DiscussionLocationUtils.conversationPage(
                id, nodes, MessageNode::getMessageId, MessageNode::getParentId, query);
        if (page == null) {
            throw missing();
        }

        List<Long> replies = page.replyIds();
        List<MessagePrivateVO> records = replies.isEmpty() ? List.of()
                : messages.visibleRows(replies).stream().map(row -> privateView(row, actor)).toList();
        return new DiscussionConversationVO<>(privateView(messages.visibleRows(List.of(page.rootId())).getFirst(), actor),
                page.page(), query.getPageSize(), page.total(), page.totalPages(), records);
    }

    /**
     * 根据id查询后台留言及版本。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<MessagePrivateVO> adminDetail(long id) {
        long actor = requireAdmin("query");
        required(messages.scoped(id, null));
        return profile(messages.visibleRows(List.of(id)).getFirst(), actor);
    }

    /**
     * 在后台回复留言。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "reply", target = "blog:message", permission = "blog:message:review", targetIdArgument = 0)
    @RateLimit(value = "message-reply", limit = 10, dimension = RateLimit.Dimension.USER)
    @CacheEvict(cacheNames = "messages", allEntries = true)
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
        BlogMessage row = BlogMessage.builder()
                .userId(actor)
                .parentId(id)
                .content(request.getContent())
                .contentFormat(request.getContentFormat())
                .notifyReply(request.getNotifyReply())
                .isSecret(secret(path) ? 1 : 0)
                .status(1)
                .location(locations.getIpLocation(ip))
                .userAgentOs(UserAgentUtils.operatingSystem(userAgent))
                .userAgentBrowser(UserAgentUtils.browser(userAgent))
                .build();

        // 3. 保存回复后登记提交后的通知，不在事务提交前发送邮件。
        messages.insert(row);
        notifications.afterMessageApproved(row.getMessageId());
        return privateView(required(messages.scoped(row.getMessageId(), null)), actor);
    }

    /**
     * 审核或删除留言前，锁住配置和全部上级留言；关闭留言开关后，仍允许管理历史留言。
     */
    private List<BlogMessage> lockAdmin(long id, String operation) {
        long actor = requireAdmin(operation);
        users.lockById(actor);
        requireAdmin(operation);
        lockConfig();
        requireAdmin(operation);
        return lockPath(id, operation, true);
    }

    /**
     * 锁住目标留言和全部上级留言，回复关系不可改，从根到目标依次加锁，遇到循环或缺失记录时拒绝操作。
     */
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

    /**
     * 检查留言能否回复，普通用户必须能看到全部上级留言；后台也不能回复待审或驳回的目标。
     */
    private void requireReplyable(List<BlogMessage> path, long actor, boolean admin) {
        if (CollUtil.isEmpty(path)) {
            return;
        }
        BlogMessage target = path.getLast();
        if (!admin && !MessageVisibilityPolicy.visible(target.getMessageId(), MessageVisibilityPolicy.index(messages.nodes()),
                actor)) {
            throw missing();
        }
        if (target.getStatus() != 1 || path.stream().anyMatch(row -> row.getStatus() != 1)) {
            throw conflict();
        }
    }

    /**
     * 判断留言是否私密，只要有上级留言是私密的，客户端传 0 也不能改为公开。
     */
    private boolean secret(List<BlogMessage> path) {
        return path.stream().anyMatch(row -> row.getIsSecret() == 1);
    }

    /**
     * 读取留言配置，与后台维护使用同一条记录，不另加缓存。
     */
    private BlogSiteConfig lockConfig() {
        BlogSiteConfig row = configs.lockByKey("message");
        if (row == null) {
            throw unavailable();
        }
        return row;
    }

    /**
     * 两项布尔值之外都算部署错误，不能用默认值悄悄放开留言。
     */
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

    /**
     * 关闭只限制新增和回复。
     */
    private void requireEnabled(JsonNode policy) {
        if (!policy.get("enabled").asBoolean()) {
            throw new BusinessException(409, "STATE_CONFLICT", "留言已关闭");
        }
    }

    /**
     * 每个锁等待结束都重查原Token和启用身份。
     */
    private long requireMember(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        if (operation != null) {
            authorization.requireScope(actor, "blog:message:" + operation);
        }
        return actor;
    }

    /**
     * 查询、审核、管理删除分别取自己的ALL。
     */
    private long requireAdmin(String operation) {
        long actor = requireMember(null);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:message:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "当前账号没有管理全部记录的权限，请联系管理员授权。");
        }
        return actor;
    }

    /**
     * 删除留言及全部子回复，配置锁阻止新增回复；锁住待删留言并重新检查权限后，从子回复向父留言依次删除。
     */
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

    /**
     * 计算留言版本，不包含昵称、头像和审计时间。
     */
    private String revision(BlogMessage row) {
        return "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(row.getUserId(), row.getParentId(), row.getContent(), row.getContentFormat(),
                        row.getIsSecret(), row.getNotifyReply(), row.getStatus(), row.getEditedAt()))) + "\"";
    }

    /**
     * 整理本人留言资料，复用公开响应中的已处理字段，额外返回回复通知设置。
     */
    private MessagePrivateVO privateView(MessageRow row, Long viewer) {
        return privateView(row, viewer, null, null);
    }

    /**
     * 组装留言响应，跳转位置仅由本人列表计算。
     */
    private MessagePrivateVO privateView(MessageRow row, Long viewer, Long rootParentId, Integer page) {
        MessagePublicVO value = publicView(row, viewer);
        return MessagePrivateVO.builder()
                .messageId(value.getMessageId())
                .parentId(value.getParentId())
                .author(value.getAuthor())
                .content(value.getContent())
                .htmlContent(value.getHtmlContent())
                .contentFormat(value.getContentFormat())
                .isSecret(value.getIsSecret())
                .status(value.getStatus())
                .editedAt(value.getEditedAt())
                .isOwn(value.isOwn())
                .createTime(value.getCreateTime())
                .updateTime(value.getUpdateTime())
                .notifyReply(row.getNotifyReply())
                .location(value.getLocation())
                .userAgentOs(value.getUserAgentOs())
                .userAgentBrowser(value.getUserAgentBrowser())
                .hasChildren(row.getHasChildren())
                .replyTarget(row.getReplyToContent() == null ? null : value.getReplyTarget())
                .siteAuthor(value.isSiteAuthor())
                .siteAdmin(value.isSiteAdmin())
                .rootParentId(rootParentId)
                .page(page)
                .build();
    }

    /**
     * 组装留言展示内容，纯文本转义并保留换行。
     */
    private MessagePublicVO publicView(MessageRow row, Long viewer) {
        String content = row.getContent();
        int format = row.getContentFormat();
        String html = format == 1 ? MarkdownUtils.toHtml(content) : "<p>" + content.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>\n") + "</p>";
        return MessagePublicVO.builder()
                .messageId(row.getMessageId())
                .parentId(row.getParentId())
                .author(ArticlePublicVO.Author.builder()
                        .userId(row.getUserId())
                        .nickname(row.getAuthorNickname())
                        .avatar(row.getAuthorAvatar())
                        .build())
                .content(content)
                .htmlContent(html)
                .contentFormat(format)
                .isSecret(row.getIsSecret())
                .status(row.getStatus())
                .editedAt(row.getEditedAt())
                .isOwn(Objects.equals(viewer, row.getUserId()))
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .location(row.getLocation())
                .userAgentOs(row.getUserAgentOs())
                .userAgentBrowser(row.getUserAgentBrowser())
                .replyCount(row.getReplyCount())
                .replyTarget(row.getParentId() == null ? null : DiscussionReplyTargetVO.builder()
                        .nickname(row.getReplyToNickname())
                        .content(row.getReplyToContent())
                        .build())
                .replyable(row.getReplyable() == null ? row.getStatus() == 1 : row.getReplyable())
                .siteAuthor(row.isSiteAuthor())
                .siteAdmin(row.isSiteAdmin())
                .build();
    }

    /**
     * 返回当前留言资料及对应版本。
     */
    private ResourceVersion<MessagePrivateVO> profile(MessageRow row, Long viewer) {
        return new ResourceVersion<>(privateView(row, viewer), revision(row));
    }

    /**
     * 检查留言是否存在，必须先由 SQL 限制归属范围。
     */
    private MessageRow required(MessageRow row) {
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /**
     * 不向前台区分私密、隐藏或不存在。
     */
    private BusinessException missing() {
        return new BusinessException(404, "NOT_FOUND", "留言不存在");
    }

    /**
     * 状态不允许当前操作。
     */
    private BusinessException conflict() {
        return new BusinessException(409, "STATE_CONFLICT", "留言状态不允许此操作");
    }

    /**
     * 不回显配置值。
     */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "留言配置不可用");
    }

    /**
     * 批量审核留言，同批通过的上级留言可供下级回复通过检查；未选中的上级留言不可见时，整批拒绝通过。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-review", target = "blog:message", permission = "blog:message:review")
    @CacheEvict(cacheNames = "messages", allEntries = true)
    public BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status) {
        // 1. 按 ID 升序加锁全部目标留言，校验版本；审核通过时检查所有未选中的祖先是否已可见。
        var sorted = BatchUtils.sorted(items);
        Set<Long> selected = new HashSet<>();
        for (var item : items) {
            selected.add(IdUtils.parse(item.getId()));
        }
        Map<Long, MessageRow> rows = new HashMap<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.getId());
            List<BlogMessage> path = lockAdmin(id, "review");
            MessageRow row = required(messages.scoped(id, null));
            RevisionUtils.check(revision(row), item.getRevision());
            if (status == 1) {
                for (BlogMessage parent : path.subList(0, path.size() - 1)) {
                    if (parent.getStatus() != 1 && !selected.contains(parent.getMessageId())) {
                        throw conflict();
                    }
                }
            }
            rows.put(id, row);
        }

        // 2. 全部校验通过后再依次更新状态、登记审核通过通知并收集新版本。
        Map<Long, String> versions = new HashMap<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.getId());
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

    /**
     * 批量删除留言，先检查所有选中的记录，再删除这些留言及其子回复；保留未选中的上级和同级留言。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:message", permission = "blog:message:moderate")
    @CacheEvict(cacheNames = "messages", allEntries = true)
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
