package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.annotation.PublicCacheRead;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
import com.xuan.entity.BlogComment;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.exception.BusinessException;
import com.xuan.location.IpLocationResolver;
import com.xuan.mapper.ArticleMapper.CommentNode;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.CommentMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.CommentRow;
import com.xuan.model.ResourceVersion;
import com.xuan.policy.CommentVisibilityPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ICommentService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UserAgentUtils;
import com.xuan.vo.ArticlePublicVO.Author;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentProfileVO;
import com.xuan.vo.CommentPublicVO;
import com.xuan.vo.DiscussionConversationVO;
import com.xuan.vo.DiscussionReplyTargetVO;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 写评论先锁操作者、配置、文章、评论；锁后重新查身份和范围，原文与审核结果一起提交。
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, BlogComment> implements ICommentService {

    private final CommentMapper comments;
    private final ObjectProvider<ICommentService> self;
    private final ArticleMapper articles;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;
    private final IpLocationResolver locations;

    /** 分页查询根评论或整组回复，在同一只读事务中检查文章和全部上级评论。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PublicCacheRead("comments")
    public PageResult<CommentPublicVO> publicComments(long article, CommentPublicQueryDTO query) {
        requirePublic(article);
        Long viewer = sessions.currentToken() == null ? null : requireMember(null);
        Long parent = IdUtils.parse(query.parentId());
        // 本人待审内容不能进入共享缓存，匿名查询仍复用公开缓存。
        if (viewer != null) {
            return readPublicComments(article, query, viewer);
        }
        if (parent != null && !CommentVisibilityPolicy.visible(articles.commentNodes(article)).contains(parent)) {
            throw missing();
        }
        return self.getObject().cachedPublicComments(article, query);
    }

    /** 缓存公开评论，外层已检查文章公开状态和全部上级评论，缓存只保存所有人都能看到的结果。 */
    @Override
    @Cacheable(cacheNames = "comments", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<CommentPublicVO> cachedPublicComments(long article, CommentPublicQueryDTO query) {
        return readPublicComments(article, query, null);
    }

    /** 整理可见回复与整组计数，只分页读取正文，保留真实的直接回复关系。 */
    private PageResult<CommentPublicVO> readPublicComments(long article, CommentPublicQueryDTO query, Long viewer) {
        Long parent = IdUtils.parse(query.parentId());
        List<CommentNode> nodes = articles.commentNodes(article);
        Set<Long> visible = CommentVisibilityPolicy.visible(nodes, viewer);
        Set<Long> replyable = viewer == null ? visible : CommentVisibilityPolicy.visible(nodes);
        if (parent != null && !visible.contains(parent)) {
            throw missing();
        }

        Map<Long, List<CommentNode>> children = new HashMap<>();
        for (CommentNode node : nodes) {
            if (visible.contains(node.getCommentId())) {
                children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
            }
        }
        List<CommentNode> ordered = new ArrayList<>(children.getOrDefault(null, List.of()));
        for (int index = 0; index < ordered.size(); index++) {
            ordered.addAll(children.getOrDefault(ordered.get(index).getCommentId(), List.of()));
        }
        Map<Long, Long> replyCounts = new HashMap<>();
        // 从最深层向上累计，回复数量包含全部可见后代。
        for (int index = ordered.size() - 1; index >= 0; index--) {
            CommentNode node = ordered.get(index);
            if (node.getParentId() != null) {
                replyCounts.merge(node.getParentId(), replyCounts.getOrDefault(node.getCommentId(), 0L) + 1, Long::sum);
            }
        }
        List<CommentNode> selected = new ArrayList<>(children.getOrDefault(parent, List.of()));
        if (parent != null) {
            for (int index = 0; index < selected.size(); index++) {
                selected.addAll(children.getOrDefault(selected.get(index).getCommentId(), List.of()));
            }
        }
        Set<Long> selectedIds = new HashSet<>();
        for (CommentNode node : selected) {
            selectedIds.add(node.getCommentId());
        }
        // 先按时间选出当前页，再查正文，避免把整组回复 ID 传进 SQL。
        List<Long> visibleIds = nodes.stream().map(CommentNode::getCommentId).filter(selectedIds::contains).toList();
        if (parent == null) {
            visibleIds = visibleIds.reversed();
        }
        int from = Math.min(visibleIds.size(), (query.page() - 1) * query.pageSize());
        List<Long> pageIds = visibleIds.subList(from, Math.min(visibleIds.size(), from + query.pageSize()));
        List<CommentPublicVO> records = new ArrayList<>();
        if (!pageIds.isEmpty()) {
            for (CommentRow row : comments.publicRows(article, pageIds, parent != null)) {
                row.setReplyCount(replyCounts.getOrDefault(row.getCommentId(), 0L));
                CommentPublicVO value = publicView(row);
                value.setReplyable(replyable.contains(row.getCommentId()));
                records.add(value);
            }
        }
        return new PageResult<>(query.page(), query.pageSize(), Integer.toString(visibleIds.size()),
                Long.toString((visibleIds.size() + (long) query.pageSize() - 1) / query.pageSize()), records);
    }

    /** 配置和文章锁内重新检查开关、审核策略和父节点，不把锁前的公开检查当成写入依据。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public CommentOwnVO create(long article, CommentCreateDTO request, String ip, String userAgent) {
        long actor = requireMember("blog:comment:create");
        users.lockById(actor);
        BlogSiteConfig config = lockConfig();
        articles.scoped(article, null, true);
        requireMember("blog:comment:create");
        requirePublic(article);
        Long parentId = request.parentId();
        if (parentId != null) {
            CommentRow parent = requireComment(comments.scoped(parentId, null, null, false));
            if (parent.getArticleId() != article) {
                throw missing();
            }
            comments.scoped(parentId, null, null, true);
        }
        // 等完所有锁再查原 Token 和当前状态；文章锁同时挡住审核、删除和下架。
        requireMember("blog:comment:create");
        ArticleRow current = requirePublic(article);
        JsonNode policy = policy(config);
        if (!policy.get("enabled").asBoolean() || current.getAllowComment() != 1) {
            throw conflict("评论已关闭");
        }
        if (parentId != null) {
            CommentRow parent = requireComment(comments.scoped(parentId, null, null, false));
            if (parent.getArticleId() != article) {
                throw missing();
            }
            if (parent.getStatus() != 1
                    || !CommentVisibilityPolicy.visible(articles.commentNodes(article)).contains(parentId)) {
                throw conflict("父评论已不可回复");
            }
        }
        BlogComment comment = new BlogComment();
        comment.setArticleId(article);
        comment.setUserId(actor);
        comment.setParentId(parentId);
        // 纯文本原样保存，尖括号也是文字；响应没有 HTML 字段，展示端用文本节点渲染。
        comment.setContent(request.content());
        comment.setLocation(locations.getIpLocation(ip));
        comment.setUserAgentOs(UserAgentUtils.operatingSystem(userAgent));
        comment.setUserAgentBrowser(UserAgentUtils.browser(userAgent));
        List<String> roles = authorization.getRoleList(actor, null);
        boolean reviewRequired = policy.get("reviewRequired").asBoolean()
                && !roles.contains("admin")
                && !(roles.contains("author") && current.getAuthorId() == actor);
        comment.setStatus(reviewRequired ? 0 : 1);
        comments.insert(comment);
        return ownView(comments.scoped(comment.getCommentId(), null, actor, false), new CommentOwnVO());
    }

    /** 用户只能取会话本人，SQL 不按文章公开状态删掉历史评论。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CommentOwnVO> ownComments(CommentOwnQueryDTO query) {
        long actor = requireMember(null);
        return PageResult.from(comments.commentPage(new Page<>(query.page(), query.pageSize()),
                        IdUtils.parse(query.articleId()), actor, query.status(), null)
                .convert(row -> ownView(row, new CommentOwnVO())));
    }

    /** 根据id查询本人评论及版本，不受文章公开状态影响。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<CommentOwnVO> ownDetail(long id) {
        CommentRow row = requireComment(comments.scoped(id, null, requireMember(null), false));
        return new ResourceVersion<>(ownView(row, new CommentOwnVO()), revision(row));
    }

    /** 本人删除整条分支不要求版本，ALL 也不能把别人评论变成本人。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public void deleteOwn(long id) {
        CommentRow row = lockComment(id, "deleteOwn", true);
        deleteTree(row, true);
    }

    /** 分页查询后台评论或一层回复，先按本次查询权限检查父评论的文章范围。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CommentAdminVO> adminComments(CommentAdminQueryDTO query) {
        Long author = requireAdmin("query");
        Long parent;
        if ("0".equals(query.parentId())) {
            parent = 0L;
        } else {
            parent = IdUtils.parse(query.parentId());
        }
        if (parent != null && parent != 0L) {
            requireComment(comments.scoped(parent, author, null, false));
        }
        return PageResult.from(comments.adminPage(new Page<>(query.page(), query.pageSize()),
                        IdUtils.parse(query.articleId()), IdUtils.parse(query.userId()), query.status(), author, parent)
                .convert(this::adminView));
    }

    /** 查询主记录及整组回复，只读取当前页正文；未传页码时定位选中的记录。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DiscussionConversationVO<CommentAdminVO> conversation(long id, DiscussionConversationQueryDTO query) {
        Long author = requireAdmin("query");
        var target = requireComment(comments.scoped(id, author, null, false));
        List<CommentNode> nodes = articles.commentNodes(target.getArticleId());
        Map<Long, CommentNode> byId = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (CommentNode node : nodes) {
            byId.put(node.getCommentId(), node);
            children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getCommentId());
        }

        // 先找到主记录，缺失父节点或循环不能拼成另一组对话。
        long root = id;
        Set<Long> ancestors = new HashSet<>();
        while (true) {
            CommentNode node = byId.get(root);
            if (node == null || !ancestors.add(root)) {
                throw missing();
            }
            if (node.getParentId() == null) {
                break;
            }
            root = node.getParentId();
        }
        Set<Long> selected = new HashSet<>();
        List<Long> descendants = new ArrayList<>(children.getOrDefault(root, List.of()));
        for (int index = 0; index < descendants.size(); index++) {
            long current = descendants.get(index);
            if (selected.add(current)) {
                descendants.addAll(children.getOrDefault(current, List.of()));
            }
        }
        // nodes已按时间和id正序排列，正文分页前先确定选中记录的位置。
        List<Long> ordered = nodes.stream().map(CommentNode::getCommentId).filter(selected::contains).toList();
        int page = query.page() == null ? Math.max(0, ordered.indexOf(id)) / query.pageSize() + 1 : query.page();
        int from = Math.min(ordered.size(), (page - 1) * query.pageSize());
        List<Long> ids = ordered.subList(from, Math.min(ordered.size(), from + query.pageSize()));
        List<CommentAdminVO> records = ids.isEmpty() ? List.of()
                : comments.publicRows(target.getArticleId(), ids, true).stream().map(this::adminView).toList();
        return new DiscussionConversationVO<>(adminView(requireComment(comments.scoped(root, author, null, false))),
                page, query.pageSize(), Integer.toString(ordered.size()),
                Long.toString((ordered.size() + (long) query.pageSize() - 1) / query.pageSize()), records);
    }

    /** 根据id查询评论详情及版本，在同一个只读事务中读取，非公开文章的评论也能查询。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CommentProfileVO detail(long id) {
        return profile(requireComment(comments.scoped(id, requireAdmin("query"), null, false)));
    }

    /** 审核评论，即使状态不变也先检查版本；驳回父评论后不用逐条修改下级回复，查询时会重新判断是否可见。 */
    private CommentProfileVO review(long id, int status, String expected) {
        CommentRow row = lockComment(id, "review", false);
        checkRevision(row, expected);
        if (row.getStatus() != status) {
            row.setStatus(status);
            comments.change(row, requireAdmin("review"), null);
        }
        return profile(comments.scoped(id, requireAdmin("review"), null, false));
    }

    /** 锁前仅定位文章，锁后重新执行本次操作的身份和 SQL 范围检查。 */
    private CommentRow lockComment(long id, String operation, boolean own) {
        long actor = sessions.currentUserId();
        users.lockById(actor);
        Long author = own ? null : requireAdmin(operation);
        Long user = own ? requireMember("blog:comment:" + operation) : null;
        CommentRow located = requireComment(comments.scoped(id, author, user, false));
        lockConfig();
        articles.scoped(located.getArticleId(), null, true);
        author = own ? null : requireAdmin(operation);
        user = own ? requireMember("blog:comment:" + operation) : null;
        comments.scoped(id, author, user, true);
        author = own ? null : requireAdmin(operation);
        user = own ? requireMember("blog:comment:" + operation) : null;
        return requireComment(comments.scoped(id, author, user, false));
    }

    /** 配置行缺失是部署错误，不能悄悄用默认值放开评论。 */
    private BlogSiteConfig lockConfig() {
        BlogSiteConfig config = comments.lockConfig();
        if (config == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "评论配置不可用");
        }
        return config;
    }

    /** 只接受现有两项布尔策略，错误配置拒绝新增，不把内部 JSON 回显给用户。 */
    private JsonNode policy(BlogSiteConfig config) {
        String value = config.getConfigValue();
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length <= 16 * 1024 && config.getIsPublic() == 0) {
            try {
                JsonNode node = json.readTree(value);
                if (node != null && node.isObject() && node.size() == 2 && node.has("enabled")
                        && node.get("enabled").isBoolean() && node.has("reviewRequired")
                        && node.get("reviewRequired").isBoolean()) {
                    return node;
                }
            } catch (JacksonException exception) {
                throw new BusinessException(503, "SERVICE_UNAVAILABLE", "评论配置不可用");
            }
        }
        throw new BusinessException(503, "SERVICE_UNAVAILABLE", "评论配置不可用");
    }

    /** 每次重查原会话，不能只信 Sa-Token 在同一请求里的身份缓存。 */
    private long requireMember(String permission) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        if (permission != null) {
            authorization.requireScope(actor, permission);
        }
        return actor;
    }

    /** 后台两项权限都要满足，返回本次操作自己的文章作者范围。 */
    private Long requireAdmin(String operation) {
        long actor = requireMember("admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:comment:" + operation)) ? null : actor;
    }

    /**
     * 确认文章当前公开，草稿、私密、下架及回收站统一返回 404。
     */
    private ArticleRow requirePublic(long id) {
        ArticleRow row = articles.publicById(id, LocalDateTime.now(ZoneOffset.UTC));
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "文章不存在");
        }
        return row;
    }

    /**
     * 评论不存在或查询范围外时统一返回 404。
     */
    private CommentRow requireComment(CommentRow row) {
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /** 删除评论及全部子回复，配置和文章锁阻止新增回复；锁住评论并重新检查权限后，从子回复向父评论依次删除。 */
    private void deleteTree(CommentRow row, boolean own) {
        var nodes = articles.lockComments(row.getArticleId());
        Long author = own ? null : requireAdmin("moderate");
        Long user = own ? requireMember("blog:comment:deleteOwn") : null;
        requireComment(comments.scoped(row.getCommentId(), author, user, false));

        Map<Long, List<Long>> children = new HashMap<>();
        for (var node : nodes) {
            children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getCommentId());
        }
        List<Long> ids = new ArrayList<>();
        ids.add(row.getCommentId());
        for (int index = 0; index < ids.size(); index++) {
            ids.addAll(children.getOrDefault(ids.get(index), List.of()));
        }
        for (long id : ids.reversed()) {
            articles.deleteComment(row.getArticleId(), id);
        }
    }

    /**
     * 用评论业务字段生成版本，不把浏览量或审计时间当作编辑冲突。
     */
    private String revision(BlogComment row) {
        return "\"" + TokenUtils.digest(json.writeValueAsString(Arrays.asList(row.getCommentId(), row.getArticleId(),
                row.getUserId(), row.getParentId(), row.getContent(), row.getStatus()))) + "\"";
    }

    /** 范围确认后才报版本错误，旧版本不能借同目标审核绕过检查。 */
    private void checkRevision(BlogComment row, String expected) {
        if (expected == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "尚未取得编辑版本，请重新打开详情后再操作。");
        }
        if (!expected.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "编辑版本无效，请保留当前输入，重新打开详情后再操作。");
        }
        if (!revision(row).equals(expected)) {
            throw new BusinessException(412, "REVISION_CONFLICT", "评论已修改，请重新获取");
        }
    }

    /**
     * 把后台评论详情与当前版本一起返回。
     */
    private CommentProfileVO profile(CommentRow row) {
        return new CommentProfileVO(adminView(row), revision(row));
    }

    /**
     * 组装本人可读的评论资料。
     */
    private <T extends CommentOwnVO> T ownView(CommentRow row, T value) {
        value.setCommentId(row.getCommentId());
        value.setArticleId(row.getArticleId());
        value.setUserId(row.getUserId());
        value.setParentId(row.getParentId());
        value.setContent(row.getContent());
        value.setLocation(row.getLocation());
        value.setUserAgentOs(row.getUserAgentOs());
        value.setUserAgentBrowser(row.getUserAgentBrowser());
        value.setStatus(row.getStatus());
        value.setCreateTime(row.getCreateTime());
        value.setUpdateTime(row.getUpdateTime());
        return value;
    }

    /**
     * 在评论资料中补充作者公开资料与文章标题，供后台审核使用。
     */
    private CommentAdminVO adminView(CommentRow row) {
        CommentAdminVO value = ownView(row, new CommentAdminVO());
        value.setAuthor(author(row));
        value.setArticleTitle(row.getArticleTitle());
        value.setHasChildren(row.getHasChildren());
        value.setReplyTarget(row.getParentId() == null ? null
                : new DiscussionReplyTargetVO(row.getReplyToNickname(), row.getReplyToContent()));
        value.setArticleAuthor(row.isArticleAuthor());
        value.setSiteAuthor(row.isSiteAuthor());
        value.setSiteAdmin(row.isSiteAdmin());
        return value;
    }

    /**
     * 只返回前台评论字段及可见回复数。
     */
    private CommentPublicVO publicView(CommentRow row) {
        CommentPublicVO value = new CommentPublicVO();
        value.setCommentId(row.getCommentId());
        value.setArticleId(row.getArticleId());
        value.setParentId(row.getParentId());
        value.setContent(row.getContent());
        value.setLocation(row.getLocation());
        value.setUserAgentOs(row.getUserAgentOs());
        value.setUserAgentBrowser(row.getUserAgentBrowser());
        value.setStatus(row.getStatus());
        value.setCreateTime(row.getCreateTime());
        value.setUpdateTime(row.getUpdateTime());
        value.setAuthor(author(row));
        value.setReplyCount(row.getReplyCount());
        value.setSiteAuthor(row.isSiteAuthor());
        value.setSiteAdmin(row.isSiteAdmin());
        value.setReplyTarget(row.getParentId() == null ? null
                : new DiscussionReplyTargetVO(row.getReplyToNickname(), row.getReplyToContent()));
        return value;
    }

    /**
     * 返回作者的公开昵称和头像。
     */
    private Author author(CommentRow row) {
        return new Author(row.getUserId(), row.getAuthorNickname(),
                row.getAuthorAvatar());
    }

    /**
     * 统一返回评论不存在，避免暴露范围外记录。
     */
    private BusinessException missing() {
        return new BusinessException(404, "NOT_FOUND", "评论不存在");
    }

    /**
     * 当前评论状态不允许操作时返回 409。
     */
    private BusinessException conflict(String message) {
        return new BusinessException(409, "STATE_CONFLICT", message);
    }

    /** 批量审核评论，先加锁并检查全部目标，结果按原来的选择顺序返回。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-review", target = "blog:comment", permission = "blog:comment:review")
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status) {
        var sorted = BatchUtils.sorted(items);
        lockBatchArticles(items.stream().map(item -> IdUtils.parse(item.id())).toList(), "review");
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String expected = item.revision();
            CommentRow row = lockComment(id, "review", false);
            checkRevision(row, expected);
        }
        Map<Long, String> versions = new HashMap<>();
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            versions.put(id, review(id, status, item.revision()).revision());
        }
        return BatchUtils.versions(items, versions);
    }


    /** 父子混选先全部核验，再一起删除选中的评论和全部子回复；子回复数量不受选择上限截断。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:comment", permission = "blog:comment:moderate")
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public void moderateBatch(List<String> ids) {
        var sorted = BatchUtils.ids(ids);
        lockBatchArticles(sorted, "moderate");
        Map<Long, CommentRow> selected = new HashMap<>();
        for (long id : sorted) {
            selected.put(id, lockComment(id, "moderate", false));
        }
        for (long id : sorted) {
            if (comments.selectById(id) != null) {
                deleteTree(selected.get(id), false);
            }
        }
    }

    /** 评论先锁配置和全部文章，再按同层ID锁定评论，不能按评论ID打乱文章锁顺序。 */
    private void lockBatchArticles(List<Long> ids, String operation) {
        users.lockById(sessions.currentUserId());
        Long author = requireAdmin(operation);
        lockConfig();
        Set<Long> articleIds = new TreeSet<>();
        for (long id : ids) {
            articleIds.add(requireComment(comments.scoped(id, author, null, false)).getArticleId());
        }
        for (long id : articleIds) {
            articles.scoped(id, null, true);
            requireAdmin(operation);
        }
    }

}
