package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.content.CommentVisibility;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.entity.BlogComment;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.CommentMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.CommentRow;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ICommentService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.ArticlePublicVO.Author;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentProfileVO;
import com.xuan.vo.CommentPublicVO;
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
import java.util.Arrays;

/**
 * 写评论先锁操作者、配置、文章、评论；锁后重新查身份和范围，原文与审核结果一起提交。
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, BlogComment> implements ICommentService {

    private static final String DELETED = "该评论已删除";
    private final CommentMapper comments;
    private final ObjectProvider<ICommentService> self;
    private final ArticleMapper articles;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** 同一快照里确认文章和完整父链，再分页直接回复，关闭评论不隐藏历史记录。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CommentPublicVO> publicComments(long article, CommentPublicQueryDTO query) {
        requirePublic(article);
        Long parent = IdUtils.parse(query.parentId());
        if (parent != null && !CommentVisibility.visible(articles.commentNodes(article)).contains(parent)) {
            throw missing();
        }
        return self.getObject().cachedPublicComments(article, query);
    }

    /** 外层已在当前快照校验 PUBLIC 与祖先链，缓存只保存共同公开结果。 */
    @Override
    @Cacheable(cacheNames = "comments", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<CommentPublicVO> cachedPublicComments(long article, CommentPublicQueryDTO query) {
        return PageResult.from(comments.publicPage(new Page<>(query.page(), query.pageSize()), article, IdUtils.parse(query.parentId()))
                .convert(this::publicView));
    }

    /** 配置和文章锁内重新检查开关、审核策略和父节点，不把锁前的公开检查当成写入依据。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public CommentOwnVO create(long article, CommentCreateDTO request) {
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
        // 等完所有锁再查原 Token 和当前状态；文章锁同时挡住审核、占位和下架。
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
                    || !CommentVisibility.visible(articles.commentNodes(article)).contains(parentId)) {
                throw conflict("父评论已不可回复");
            }
        }
        BlogComment comment = new BlogComment();
        comment.setArticleId(article);
        comment.setUserId(actor);
        comment.setParentId(parentId);
        // 纯文本原样保存，尖括号也是文字；响应没有 HTML 字段，展示端用文本节点渲染。
        comment.setContent(request.content());
        comment.setStatus(policy.get("reviewRequired").asBoolean() ? 0 : 1);
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

    /** 本人占位删除不要求版本，ALL 也不能把别人评论变成本人。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public void deleteOwn(long id) {
        CommentRow row = lockComment(id, "deleteOwn", true);
        placeholder(row, null, sessions.currentUserId());
    }

    /** 每次 query 单独计算文章作者范围，评论者筛选只会缩小结果。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CommentAdminVO> adminComments(CommentAdminQueryDTO query) {
        Long author = requireAdmin("query");
        return PageResult.from(comments.commentPage(new Page<>(query.page(), query.pageSize()),
                        IdUtils.parse(query.articleId()), IdUtils.parse(query.userId()), query.status(), author)
                .convert(this::adminView));
    }

    /** 详情和版本取同一快照，可以读取非公开文章下的评论。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CommentProfileVO detail(long id) {
        return profile(requireComment(comments.scoped(id, requireAdmin("query"), null, false)));
    }

    /** 幂等目标也先查版本；父评论驳回后无需改后代，公开链会即时重新计算。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "review", target = "blog:comment", permission = "blog:comment:review", targetIdArgument = 0)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public CommentProfileVO review(long id, int status, String expected) {
        CommentRow row = lockComment(id, "review", false);
        checkRevision(row, expected);
        if (row.getStatus() == 3) {
            throw conflict("已删除评论不能重新审核");
        }
        if (row.getStatus() != status) {
            row.setStatus(status);
            comments.change(row, requireAdmin("review"), null);
        }
        return profile(comments.scoped(id, requireAdmin("review"), null, false));
    }

    /** 管理占位删除只查 moderate 的范围，不借 query 或 review 的 ALL 扩大权限。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:comment", permission = "blog:comment:moderate", targetIdArgument = 0)
    @CacheEvict(cacheNames = "comments", allEntries = true)
    public void moderate(long id) {
        CommentRow row = lockComment(id, "moderate", false);
        placeholder(row, requireAdmin("moderate"), null);
    }

    /** 锁前仅定位文章，锁后重新执行本次操作的身份和 SQL 范围检查。 */
    private CommentRow lockComment(long id, String operation, boolean own) {
        long actor = sessions.currentUserId();
        users.lockById(actor);
        Long author = own ? null : requireAdmin(operation);
        Long user = own ? requireMember("blog:comment:deleteOwn") : null;
        CommentRow located = requireComment(comments.scoped(id, author, user, false));
        lockConfig();
        articles.scoped(located.getArticleId(), null, true);
        author = own ? null : requireAdmin(operation);
        user = own ? requireMember("blog:comment:deleteOwn") : null;
        comments.scoped(id, author, user, true);
        author = own ? null : requireAdmin(operation);
        user = own ? requireMember("blog:comment:deleteOwn") : null;
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

    /** 不物理删除也不改父链，已经占位时不重复更新时间。 */
    private void placeholder(CommentRow row, Long author, Long user) {
        if (row.getStatus() != 3) {
            row.setContent(DELETED);
            row.setStatus(3);
            comments.change(row, author, user);
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
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!expected.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
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
     * 组装本人可读的评论资料，删除记录只返回占位内容。
     */
    private <T extends CommentOwnVO> T ownView(CommentRow row, T value) {
        value.setCommentId(row.getCommentId());
        value.setArticleId(row.getArticleId());
        value.setUserId(row.getUserId());
        value.setParentId(row.getParentId());
        value.setContent(row.getStatus() == 3 ? DELETED : row.getContent());
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
        return value;
    }

    /**
     * 只返回前台评论字段及可见回复数，删除占位不还原原文。
     */
    private CommentPublicVO publicView(CommentRow row) {
        CommentPublicVO value = new CommentPublicVO();
        value.setCommentId(row.getCommentId());
        value.setArticleId(row.getArticleId());
        value.setParentId(row.getParentId());
        value.setContent(row.getStatus() == 3 ? DELETED : row.getContent());
        value.setStatus(row.getStatus());
        value.setCreateTime(row.getCreateTime());
        value.setUpdateTime(row.getUpdateTime());
        value.setAuthor(author(row));
        value.setReplyCount(row.getReplyCount());
        return value;
    }

    /**
     * 删除占位不返回作者，其余只返回公开昵称和头像。
     */
    private Author author(CommentRow row) {
        return row.getStatus() == 3 ? null : new Author(row.getUserId(), row.getAuthorNickname(),
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
}
