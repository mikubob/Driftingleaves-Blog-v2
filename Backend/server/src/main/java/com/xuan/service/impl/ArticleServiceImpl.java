package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.content.ArticleResources;
import com.xuan.content.ArticleResources.References;
import com.xuan.content.CommentVisibility;
import com.xuan.content.SubscriptionNotifications;
import com.xuan.dto.AdminArticleQueryDTO;
import com.xuan.dto.ArticleQueryDTO;
import com.xuan.dto.ArticleWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogArticle;
import com.xuan.entity.BlogArticleContent;
import com.xuan.entity.BlogCategory;
import com.xuan.entity.BlogFile;
import com.xuan.entity.BlogTag;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.ArticleMapper.CommentNode;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ArticleFilter;
import com.xuan.model.ArticleRow;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IArticleService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.utils.MarkdownUtils;
import com.xuan.utils.MarkdownUtils.ArticleContent;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.ArchiveVO;
import com.xuan.vo.ArticleAdminDetailVO;
import com.xuan.vo.ArticleAdminVO;
import com.xuan.vo.ArticleProfileVO;
import com.xuan.vo.ArticlePublicDetailVO;
import com.xuan.vo.ArticlePublicVO;
import com.xuan.vo.HotArticleVO;
import com.xuan.vo.AuthorPublicVO;
import com.xuan.vo.LikedArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 文章写入先锁操作者，再升序锁文件、锁文章；原会话要重查，正文、标签和成功日志一起提交。
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, BlogArticle> implements IArticleService {

    private final ArticleMapper articles;
    private final ObjectProvider<IArticleService> self;
    private final SubscriptionNotifications notifications;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final ArticleResources resources;
    private final JsonMapper json;

    /**
     * 文件必须比文章先加锁，保存已锁对象，后面不能倒过来补拿新文件锁。
     */
    private record LockedArticle(ArticleRow article, Map<Long, BlogFile> files) {

    }

    /**
     * 同一快照里查公开列表，ID 和月份先检查；分类隐藏只影响返回的分类资料。
     *
     * @param query 公开请求过滤
     * @return 稳定置顶/发布时间/ID倒序分页，不改变阅读数
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<ArticlePublicVO> publicArticles(ArticleQueryDTO query) {
        PageResult<ArticlePublicVO> cached = self.getObject().cachedPublicArticles(query);
        List<ArticlePublicVO> records = cached.records().stream().map(item -> {
            ArticlePublicVO copy = new ArticlePublicVO();
            org.springframework.beans.BeanUtils.copyProperties(item, copy);
            return copy;
        }).toList();
        PageResult<ArticlePublicVO> page = new PageResult<>(cached.page(), cached.pageSize(), cached.total(), cached.totalPages(), records);
        for (ArticlePublicVO value : page.records()) {
            ArticleRow current = articles.publicById(value.getArticleId(), now());
            if (current == null) { return loadPublicArticles(query); }
            value.setViewCount(current.getViewCount());
            value.setLikeCount(current.getLikeCount());
            value.setCommentCount(commentCount(current.getArticleId()));
        }
        return page;
    }

    /** 集合及静态字段缓存经容器代理调用，当前互动数由外层事务补齐。 */
    @Override
    @Caching(cacheable = {
            @Cacheable(cacheNames = "articles", key = "@publicCacheKeys.key(#root.methodName, #root.args)", condition = "#query.keyword() == null && #query.page() <= 10 && #query.pageSize() <= 20"),
            @Cacheable(cacheNames = "search", key = "@publicCacheKeys.key(#root.methodName, #root.args)", condition = "#query.keyword() != null && #query.page() <= 3 && #query.pageSize() <= 20")
    })
    public PageResult<ArticlePublicVO> cachedPublicArticles(ArticleQueryDTO query) {
        PageResult<ArticlePublicVO> page = loadPublicArticles(query);
        for (ArticlePublicVO value : page.records()) {
            value.setViewCount(null); value.setLikeCount(null); value.setCommentCount(null);
        }
        return page;
    }

    private PageResult<ArticlePublicVO> loadPublicArticles(ArticleQueryDTO query) {
        YearMonth month = query.month() == null ? null : YearMonth.parse(query.month());
        ArticleFilter filter = new ArticleFilter(true, null, query.keyword(), IdUtils.parse(query.categoryId()),
                IdUtils.parse(query.tagId()), IdUtils.parse(query.authorId()), null, null, 0,
                month == null ? null : month.atDay(1).atStartOfDay(), upper(month), now());
        return PageResult.from(articles.articlePage(new Page<>(query.page(), query.pageSize()), filter)
                .convert(row -> publicView(row, new ArticlePublicVO())));
    }

    /** 作者发现只依赖 PUBLIC 文章，列表与 total 使用同一只读快照。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "authors", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<AuthorPublicVO> publicAuthors(PageQueryDTO query) {
        return PageResult.from(articles.authorPage(new Page<>(query.page(), query.pageSize()), now()));
    }

    /** 不能通过作者页判断没有公开作品的账号是否存在。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "authors", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public AuthorPublicVO publicAuthor(long userId) {
        AuthorPublicVO value = articles.publicAuthor(userId, now());
        if (value == null) {
            throw new BusinessException(404, "NOT_FOUND", "作者不存在");
        }
        return value;
    }

    /** 作者和点赞者是不同身份，查询本人点赞不需要后台或点赞写权限。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<LikedArticleVO> likedArticles(PageQueryDTO query) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        return PageResult.from(articles.likedPage(new Page<>(query.page(), query.pageSize()), actor, now())
                .convert(row -> new LikedArticleVO(row.getLikedAt(), publicView(row, new ArticlePublicVO()))));
    }

    /**
     * 文章锁内取公开详情、上下篇和阅读信息，全部成功后再原子加一次阅读。
     *
     * @param slug 已校验的大小写敏感slug
     * @return 安全公开详情；任何正文或数据库失败回滚计数，不以Token放宽PUBLIC
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArticlePublicDetailVO publicDetail(String slug) {
        LocalDateTime at = now();
        ArticleRow row = requireArticle(articles.publicBySlug(slug, at, true));
        articles.lockCurrentTags(row.getArticleId());
        if (row.getCategoryId() != null) {
            articles.lockCategory(row.getCategoryId());
        }
        row = requireArticle(articles.publicBySlug(slug, at, false));
        // 先锁定文章并确认公开状态，再读正文、关联和互动；查推荐不会增加阅读量。
        String markdown = content(row.getArticleId()).getMarkdownContent();
        References references = resources.inspect(row.getArticleId(), markdown, row.getCoverUrl());
        ArticlePublicDetailVO cached = self.getObject().cachedPublicDetail(row, markdown, references.html());
        ArticlePublicDetailVO value = new ArticlePublicDetailVO();
        org.springframework.beans.BeanUtils.copyProperties(cached, value);
        value.setViewCount(row.getViewCount());
        value.setLikeCount(row.getLikeCount());
        value.setCommentCount(commentCount(row.getArticleId()));
        articles.incrementViews(row.getArticleId());
        value.setViewCount(row.getViewCount().add(BigInteger.ONE));
        return value;
    }

    /** 只缓存公开静态响应片段；原事务每次重新检查 PUBLIC、当前正文、附件及真实互动。 */
    @Override
    @Cacheable(cacheNames = "articleDetail", key = "T(com.xuan.utils.TokenUtils).digest(#row.slug + ':' + #row.articleId + ':' + #markdown + ':' + #row.coverUrl)")
    public ArticlePublicDetailVO cachedPublicDetail(ArticleRow row, String markdown, String html) {
        LocalDateTime at = now();
        ArticleContent parsed = MarkdownUtils.article(markdown);
        ArticlePublicDetailVO value = publicView(row, new ArticlePublicDetailVO());
        value.setHtmlContent(html);
        value.setWordCount(parsed.wordCount());
        value.setReadingMinutes(parsed.readingMinutes());
        value.setPrevArticle(articles.adjacent(row, true, at));
        value.setNextArticle(articles.adjacent(row, false, at));
        value.setRelatedArticles(value.getCategory() == null ? List.of() : articles.related(row, at));
        value.setViewCount(null); value.setLikeCount(null); value.setCommentCount(null);
        return value;
    }

    /**
     * 同一快照里按 UTC 月份统计公开文章，分页和统计共用条件。
     *
     * @param page 已校验的页码
     * @param pageSize 已校验的页大小
     * @return 月份倒序分页，不读取正文或改变阅读数
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "archives", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<ArchiveVO> archives(int page, int pageSize) {
        return PageResult.from(articles.archives(new Page<>(page, pageSize), now()));
    }

    /**
     * 先实时查后台入口和 query 权限，SQL 再限制作者；authorId 只是额外条件，不能扩大 SELF。
     *
     * @param query 后台分页、状态、回收站及字面关键词
     * @return 修改时间及ID倒序列表，不携带正文或版本
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<ArticleAdminVO> adminArticles(AdminArticleQueryDTO query) {
        Long scope = requireAdmin("query");
        ArticleFilter filter = new ArticleFilter(false, scope, query.keyword(), IdUtils.parse(query.categoryId()),
                IdUtils.parse(query.tagId()), IdUtils.parse(query.authorId()), query.status(), query.visibility(),
                query.trash(), null, null, now());
        return PageResult.from(articles.articlePage(new Page<>(query.page(), query.pageSize()), filter)
                .convert(row -> adminView(row, new ArticleAdminVO())));
    }

    /**
     * 文章资料、正文、升序标签和当前分类在同一只读快照里取，回收站也能查。
     *
     * @param id 目标文章
     * @return 约定的后台详情和整体版本；范围外统一404
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ArticleProfileVO detail(long id) {
        return profile(requireArticle(articles.scoped(id, requireAdmin("query"), false)));
    }

    /**
     * 锁住操作者后再查身份和创建权限，作者固定取会话、状态固定草稿，关联一起提交。
     *
     * @param request 已校验的字段及 POST 默认值，创建时不得包含本站附件
     * @return 创建后的完整详情及版本；唯一键或关联失败回滚元数据、正文、标签与审计
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "blog:article", permission = "blog:article:create")
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "tags", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true),
            @CacheEvict(cacheNames = "comments", allEntries = true)
    })
    public ArticleProfileVO create(ArticleWriteDTO request) {
        // 1. 锁住操作者后检查创建权限，再校验正文、封面和关联字典。
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        validateWrite(request);
        References references = resources.inspect(null, request.getMarkdownContent(), request.getCoverUrl());
        lockAssociations(request.getCategoryId(), request.getTagIds());

        // 2. 字典行也可能等待，拿齐锁后不能沿用等待前的 Token 和权限。
        requireAdmin("create");

        // 3. 以当前用户创建草稿，正文、标签和成功审计在同一事务提交。
        BlogArticle article = new BlogArticle();
        article.setAuthorId(sessions.currentUserId());
        article.setStatus(0);
        article.setViewCount(BigInteger.ZERO);
        apply(article, request, references.coverUrl());
        articles.insert(article);
        saveBody(article.getArticleId(), request, references.html());
        return profile(articles.scoped(article.getArticleId(), null, false));
    }

    /**
     * 先拿文件锁、文章锁并重查权限，再检查版本、完整替换文章；已发布还要单独查 publish 范围。
     *
     * @param id 目标文章
     * @param request PUT全部允许字段
     * @param revision 当前整体版本，状态或同值操作不能绕过检查
     * @return 新详情及版本；引用、关联、权限或审计失败整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:article", permission = "blog:article:update", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "tags", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true),
            @CacheEvict(cacheNames = "comments", allEntries = true)
    })
    public ArticleProfileVO update(long id, ArticleWriteDTO request, String revision) {
        validateWrite(request);
        References next = resources.inspect(id, request.getMarkdownContent(), request.getCoverUrl());
        LockedArticle locked = lockArticle(id, "update", revision, next.fileIds());
        ArticleRow article = locked.article();
        requireNormal(article);
        if (article.getStatus() == 1) {
            requirePublishable(request.getMarkdownContent(), request.getArticleType(), request.getOriginalUrl());
        }
        // 拿锁后重新检查最终文件引用和字典，锁前读到的资料只能帮助定位，不能当最终校验。
        validateFiles(next.fileIds(), locked.files());
        lockAssociations(request.getCategoryId(), request.getTagIds());
        apply(article, request, next.coverUrl());
        articles.updateArticle(article, requireAdmin("update"));
        saveBody(id, request, next.html());
        return profile(articles.scoped(id, null, false));
    }

    /**
     * 锁事务里查 publish 范围和版本，状态只允许 0→1、1→2、2→1 或保持当前目标。
     *
     * @param id 目标文章
     * @param status 入口保证1或2
     * @param revision 当前业务版本
     * @return 新详情及版本；首次发布写当前UTC，后续保留，回收站拒绝变更
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "status", target = "blog:article", permission = "blog:article:publish", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "tags", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true),
            @CacheEvict(cacheNames = "comments", allEntries = true)
    })
    public ArticleProfileVO changeStatus(long id, int status, String revision) {
        LockedArticle locked = lockArticle(id, "publish", revision, Set.of());
        ArticleRow article = locked.article();
        requireNormal(article);
        if (article.getStatus() == 0 && status == 2) {
            throw new BusinessException(409, "STATE_CONFLICT", "草稿须先发布");
        }
        boolean firstPublic = status == 1 && article.getPublishTime() == null && article.getVisibility() == 1;
        if (status == 1) {
            String markdown = content(id).getMarkdownContent();
            requirePublishable(markdown, article.getArticleType(), article.getOriginalUrl());
            References references = resources.inspect(id, markdown, article.getCoverUrl());
            validateFiles(references.fileIds(), locked.files());
            lockAssociations(article.getCategoryId(), articles.tags(id).stream().map(BlogTag::getTagId).toList());
            if (article.getPublishTime() == null) {
                article.setPublishTime(now());
            }
        }
        article.setStatus(status);
        articles.updateArticle(article, requireAdmin("publish"));
        if (firstPublic) {
            notifications.afterArticlePublished(id);
        }
        return profile(articles.scoped(id, null, false));
    }

    /**
     * 文章锁内查 delete 范围和版本，只写回收时间，正文、关联和互动都留下。
     *
     * @param id 目标文章
     * @param revision 当前版本；已回收仍先检查该版本
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:article", permission = "blog:article:delete", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "tags", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true),
            @CacheEvict(cacheNames = "comments", allEntries = true)
    })
    public void recycle(long id, String revision) {
        ArticleRow article = lockArticle(id, "delete", revision, Set.of()).article();
        if (article.getDeletedAt() == null) {
            article.setDeletedAt(now());
            articles.updateArticle(article, requireAdmin("delete"));
        }
    }

    /**
     * 文章锁内查 restore 范围、版本和回收状态，恢复后固定下架，不自动公开。
     *
     * @param id 目标文章
     * @param revision 当前回收站版本
     * @return 清空deletedAt后的详情，首次发布时间保留，非回收站409
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "restore", target = "blog:article", permission = "blog:article:restore", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "tags", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true),
            @CacheEvict(cacheNames = "comments", allEntries = true)
    })
    public ArticleProfileVO restore(long id, String revision) {
        ArticleRow article = lockArticle(id, "restore", revision, Set.of()).article();
        if (article.getDeletedAt() == null) {
            throw new BusinessException(409, "STATE_CONFLICT", "文章不在回收站");
        }
        article.setDeletedAt(null);
        article.setStatus(2);
        articles.updateArticle(article, requireAdmin("restore"));
        return profile(articles.scoped(id, null, false));
    }

    /** 先锁完整评论树，再按子到父删除；文件不删除，成功审计与聚合清理共同提交。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "purge", target = "blog:article", permission = "blog:article:purge", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "tags", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true),
            @CacheEvict(cacheNames = "comments", allEntries = true)
    })
    public void purge(long id, String revision) {
        ArticleRow article = lockArticle(id, "purge", revision, Set.of()).article();
        if (article.getDeletedAt() == null) {
            throw new BusinessException(409, "STATE_CONFLICT", "仅允许永久删除回收站文章");
        }
        List<CommentNode> nodes = articles.lockComments(id);
        requireArticle(articles.scoped(id, requireAdmin("purge"), false));

        // 不假设父 ID 小于子 ID，也不使用有深度限制的递归 SQL。
        Map<Long, Long> parents = new HashMap<>();
        Map<Long, Integer> children = new HashMap<>();
        for (CommentNode node : nodes) {
            parents.put(node.getCommentId(), node.getParentId());
            if (node.getParentId() != null) {
                children.merge(node.getParentId(), 1, Integer::sum);
            }
        }
        ArrayDeque<Long> leaves = new ArrayDeque<>();
        for (CommentNode node : nodes) {
            if (!children.containsKey(node.getCommentId())) {
                leaves.add(node.getCommentId());
            }
        }
        while (!leaves.isEmpty()) {
            long comment = leaves.remove();
            articles.deleteComment(id, comment);
            Long parent = parents.get(comment);
            if (parent != null && children.merge(parent, -1, Integer::sum) == 0) {
                leaves.add(parent);
            }
        }
        articles.deleteScoped(id, requireAdmin("purge"));
    }

    /**
     * 同一快照里查公开榜单，本月按 UTC 首次发布时间筛选，热度仍看累计数。
     *
     * @param period site或month
     * @param metric view或like
     * @param limit 已校验的1..20
     * @return 稳定热度/发布时间/ID倒序列表，不增加阅读计数
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "rankings", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public List<HotArticleVO> rankings(String period, String metric, int limit) {
        LocalDateTime at = now();
        YearMonth month = "month".equals(period) ? YearMonth.from(at) : null;
        return articles.rankings(month == null ? null : month.atDay(1).atStartOfDay(), upper(month), at,
                "like".equals(metric), limit).stream().map(row -> new HotArticleVO(row.getArticleId(), row.getTitle(),
                row.getSlug(), row.getSummary(), row.getCoverUrl(), row.getViewCount(), row.getLikeCount(),
                "like".equals(metric) ? BigInteger.valueOf(row.getLikeCount()) : row.getViewCount(),
                row.getPublishTime())).toList();
    }

    /**
     * 原 Token、启用账号、后台入口和业务权限都要实时查，范围只合并授予本次权限的角色。
     *
     * @param operation 文章操作后缀
     * @return ALL为null，SELF为当前作者ID，交给SQL作为独立过滤
     */
    private Long requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        String scope = authorization.requireScope(actor, "blog:article:" + operation);
        if ("purge".equals(operation) && !"ALL".equals(authorization.requireScope(actor, "blog:article:delete"))) {
            return actor;
        }
        return "ALL".equals(scope) ? null : actor;
    }

    /**
     * 先锁操作者并定位旧引用，再升序锁新旧文件、锁文章，最后重查原 Token、范围和版本。
     *
     * @param id 目标文章
     * @param operation 当前操作的独立权限
     * @param revision 客户端详情版本
     * @param added 候选内容引用集合；锁前读取仅用于定位
     * @return 锁后最新文章；等待中正文/关联变化必须412，绝不晚于文章锁补拿文件锁
     */
    private LockedArticle lockArticle(long id, String operation, String revision, Set<Long> added) {
        users.lockById(sessions.currentUserId());
        ArticleRow located = requireArticle(articles.scoped(id, requireAdmin(operation), false));
        References old = resources.inspect(id, content(id).getMarkdownContent(), located.getCoverUrl());
        Set<Long> locked = new TreeSet<>(old.fileIds());
        locked.addAll(added);
        Map<Long, BlogFile> lockedFiles = new HashMap<>();
        for (long file : locked) {
            lockedFiles.put(file, articles.lockFile(file));
        }
        ArticleRow current = requireArticle(articles.scoped(id, requireAdmin(operation), true));
        // 等到文章锁后重查身份和 SQL 范围，不能拿等待前的权限或 Token 继续写。
        current = requireArticle(articles.scoped(id, requireAdmin(operation), false));
        if ("update".equals(operation) && current.getStatus() == 1) {
            requireArticle(articles.scoped(id, requireAdmin("publish"), false));
        }
        articles.lockCurrentTags(id);
        current = requireArticle(articles.scoped(id, requireAdmin(operation), false));
        checkRevision(current, revision);
        References latest = resources.inspect(id, content(id).getMarkdownContent(), current.getCoverUrl());
        if (!locked.containsAll(latest.fileIds())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "文章引用已变化，请重新获取");
        }
        return new LockedArticle(current, lockedFiles);
    }

    /**
     * 最终引用的每个文件都要存在、满足 query 权限及上传者范围，还得确认存储对象可用。
     *
     * @param ids 已在文章锁前升序取得行锁的文件ID集合
     * @param locked 文章锁前读取并持锁的文件快照；缺失记录不能在文章锁后补锁
     */
    private void validateFiles(Set<Long> ids, Map<Long, BlogFile> locked) {
        if (ids.isEmpty()) {
            return;
        }
        long actor = sessions.currentUserId();
        String scope = authorization.requireScope(actor, "blog:file:query");
        for (long id : new TreeSet<>(ids)) {
            BlogFile file = locked.get(id);
            if (file == null || articles.scopedFile(id, "SELF".equals(scope) ? actor : null) == null) {
                throw new BusinessException(400, "VALIDATION_ERROR", "引用文件不存在或不在文件权限范围内");
            }
            resources.requireAvailable(file);
        }
    }

    /**
     * 持有文章锁时再锁分类和升序标签，避免检查存在后它们又被删掉。
     *
     * @param category 分类可空，后台可引用隐藏分类
     * @param tags 入口已确认数量、不重复及ID格式的完整集合
     */
    private void lockAssociations(Long category, List<Long> tags) {
        if (category != null && articles.lockCategory(category) == null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "分类不存在");
        }
        for (long tag : new TreeSet<>(tags)) {
            if (articles.lockTag(tag) == null) {
                throw new BusinessException(400, "VALIDATION_ERROR", "标签不存在");
            }
        }
    }

    /**
     * 写入前查解码后的 Markdown UTF-8 大小和转载来源，不裁剪原文，也不抓外部地址。
     *
     * @param request 已完成JSON、Unicode、枚举和集合格式验证的请求
     */
    private void validateWrite(ArticleWriteDTO request) {
        if (request.getMarkdownContent().getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw new BusinessException(413, "PAYLOAD_TOO_LARGE", "Markdown正文超过1MiB");
        }
        if (!UrlPolicy.valid(
                request.getOriginalUrl()) || (request.getArticleType() != 1 && request.getOriginalUrl() == null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "转载和翻译必须提供合法原文URL");
        }
    }

    /**
     * 发布和编辑已发布文章都得有非空白正文及合法来源，失败不能改原状态和首次发布时间。
     *
     * @param markdown 当前或候选Markdown源
     * @param type 1原创、2转载、3翻译
     * @param original 外部来源URL；转载/翻译不得为空
     */
    private void requirePublishable(String markdown, int type, String original) {
        if (markdown.isBlank() || !UrlPolicy.valid(original) || (type != 1 && original == null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "发布须提供非空白正文及有效原文来源");
        }
    }

    /**
     * 只把允许修改的字段填进去，不能让 DTO 覆盖作者、状态、计数和发布时间等字段。
     *
     * @param article 新草稿或当前锁定文章
     * @param request 已通过入口和业务校验的完整内容
     * @param cover 已归一化的规范封面URL
     */
    private void apply(BlogArticle article, ArticleWriteDTO request, String cover) {
        article.setTitle(request.getTitle());
        article.setSlug(request.getSlug());
        article.setSummary(request.getSummary());
        article.setCategoryId(request.getCategoryId());
        article.setCoverUrl(cover);
        article.setArticleType(request.getArticleType());
        article.setOriginalUrl(request.getOriginalUrl());
        article.setVisibility(request.getVisibility());
        article.setIsTop(request.getIsTop());
        article.setAllowComment(request.getAllowComment());
    }

    /**
     * 调用方事务里完整替换正文和升序标签，插入或成功日志失败就整笔回滚。
     *
     * @param id 新建或已锁定文章ID
     * @param request 完整正文和标签集合，字典已锁定
     * @param html 服务端渲染清理的公开上下文缓存
     */
    private void saveBody(long id, ArticleWriteDTO request, String html) {
        articles.saveContent(id, request.getMarkdownContent(), html);
        articles.clearTags(id);
        for (long tag : new TreeSet<>(request.getTagIds())) {
            articles.addTag(id, tag);
        }
    }

    /**
     * 权限和范围通过后再查文章是否存在，越界也报 404，不泄露文章状态。
     *
     * @param row 已落实操作范围的SQL结果
     * @return 非空文章，缺失和越界统一404
     */
    private ArticleRow requireArticle(ArticleRow row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "文章不存在");
        }
        return row;
    }

    /**
     * 先查版本，再查是否在回收站；回收站文章不能编辑或发布。
     *
     * @param article 已锁定并通过当前版本校验的文章，回收状态返回409
     */
    private void requireNormal(BlogArticle article) {
        if (article.getDeletedAt() != null) {
            throw new BusinessException(409, "STATE_CONFLICT", "回收站文章不可编辑或发布");
        }
    }

    /**
     * 文章没正文就按存储故障处理，不能编个空正文返回，也不能创建孤立文章。
     *
     * @param id 同一快照或当前行锁下的文章ID
     * @return 存在的正文；缺失返回503使写事务回滚
     */
    private BlogArticleContent content(long id) {
        BlogArticleContent value = articles.content(id);
        if (value == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "文章正文暂不可用");
        }
        return value;
    }

    /**
     * 同一快照或锁事务里取后台详情并计算版本，Markdown 和升序 tagIds 都要算进去。
     *
     * @param row 最新文章元数据
     * @return 安全后台HTML及带引号版本，排除计数、审计时间和派生阅读信息
     */
    private ArticleProfileVO profile(ArticleRow row) {
        ArticleAdminDetailVO value = adminView(row, new ArticleAdminDetailVO());
        String markdown = content(row.getArticleId()).getMarkdownContent();
        value.setCategoryId(row.getCategoryId());
        value.setTagIds(value.getTags().stream().map(BlogTag::getTagId).toList());
        value.setMarkdownContent(markdown);
        value.setHtmlContent(
                resources.adminHtml(resources.inspect(row.getArticleId(), markdown, row.getCoverUrl()).html()));
        return new ArticleProfileVO(value, revision(row, markdown, value.getTagIds()));
    }

    /**
     * 按固定顺序计算版本，把全部可写资料、状态、首次发布时间、回收时间、Markdown 和升序标签都算进去。
     *
     * @param row 同一快照或行锁下的最新元数据
     * @param markdown 同一篇文章的原始正文，不以HTML缓存替代
     * @param tags 当前完整升序标签ID集合
     * @return 带引号SHA-256业务版本，不包含派生计数及审计时间
     */
    private String revision(BlogArticle row, String markdown, List<Long> tags) {
        return "\"" + TokenUtils.digest(json.writeValueAsString(Arrays.asList(row.getArticleId(), row.getAuthorId(),
                row.getCategoryId(), row.getTitle(), row.getSlug(), row.getSummary(), row.getCoverUrl(),
                row.getArticleType(),
                row.getOriginalUrl(), row.getStatus(), row.getVisibility(), row.getIsTop(), row.getAllowComment(),
                row.getPublishTime(), row.getDeletedAt(), markdown, tags))) + "\"";
    }

    /**
     * 拿到文章锁并检查范围后，先查单个具体版本，再查状态或是否重复操作。
     *
     * @param article 当前锁定元数据，关联标签已锁定
     * @param expected 客户端原样版本；缺失428、格式错误400、过期412
     */
    private void checkRevision(BlogArticle article, String expected) {
        if (expected == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!expected.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        String actual = revision(article, content(article.getArticleId()).getMarkdownContent(),
                articles.tags(article.getArticleId()).stream().map(BlogTag::getTagId).toList());
        if (!actual.equals(expected)) {
            throw new BusinessException(412, "REVISION_CONFLICT", "文章已修改，请重新获取");
        }
    }

    /**
     * 前台只返回约定字段，隐藏分类给 null，标签升序，作者不带邮箱或后台资料。
     *
     * @param row 已满足PUBLIC的文章元数据和公开作者资料
     * @param value 公开列表或详情模型
     * @return 同一快照或锁事务内填充的公开字段，不输出Markdown源
     */
    private <T extends ArticlePublicVO> T publicView(ArticleRow row, T value) {
        value.setArticleId(row.getArticleId());
        value.setAuthor(new ArticlePublicVO.Author(row.getAuthorId(), row.getAuthorNickname(), row.getAuthorAvatar()));
        BlogCategory category = row.getCategoryId() == null ? null : articles.category(row.getCategoryId());
        value.setCategory(category == null || category.getStatus() != 1 ? null : new ArticlePublicVO.Category(
                category.getCategoryId(), category.getCategoryName(), category.getSlug(), category.getDescription(),
                category.getSortOrder()));
        value.setTitle(row.getTitle());
        value.setSlug(row.getSlug());
        value.setSummary(row.getSummary());
        value.setCoverUrl(row.getCoverUrl());
        value.setArticleType(row.getArticleType());
        value.setOriginalUrl(row.getOriginalUrl());
        value.setIsTop(row.getIsTop());
        value.setAllowComment(row.getAllowComment());
        value.setViewCount(row.getViewCount());
        value.setLikeCount(row.getLikeCount());
        value.setCommentCount(commentCount(row.getArticleId()));
        value.setPublishTime(row.getPublishTime());
        value.setCreateTime(row.getCreateTime());
        value.setUpdateTime(row.getUpdateTime());
        value.setTags(articles.tags(row.getArticleId()).stream()
                .map(tag -> new ArticlePublicVO.Tag(tag.getTagId(), tag.getTagName(), tag.getSlug())).toList());
        return value;
    }

    /**
     * 后台返回完整管理资料和当前字典，封面保留保存值，HTML 里的附件地址另外转成后台路径。
     *
     * @param row 当前操作SQL范围内的文章
     * @param value 后台列表或详情模型
     * @return 含回收状态和真实互动计数的约定字段，不含作者私人资料
     */
    private <T extends ArticleAdminVO> T adminView(ArticleRow row, T value) {
        value.setArticleId(row.getArticleId());
        value.setAuthorId(row.getAuthorId());
        value.setAuthor(new ArticleAdminVO.Author(row.getAuthorId(), row.getAuthorNickname()));
        value.setCategory(row.getCategoryId() == null ? null : articles.category(row.getCategoryId()));
        value.setTitle(row.getTitle());
        value.setSlug(row.getSlug());
        value.setSummary(row.getSummary());
        value.setCoverUrl(row.getCoverUrl());
        value.setArticleType(row.getArticleType());
        value.setOriginalUrl(row.getOriginalUrl());
        value.setStatus(row.getStatus());
        value.setVisibility(row.getVisibility());
        value.setIsTop(row.getIsTop());
        value.setAllowComment(row.getAllowComment());
        value.setViewCount(row.getViewCount());
        value.setLikeCount(row.getLikeCount());
        value.setCommentCount(commentCount(row.getArticleId()));
        value.setPublishTime(row.getPublishTime());
        value.setDeletedAt(row.getDeletedAt());
        value.setCreateTime(row.getCreateTime());
        value.setUpdateTime(row.getUpdateTime());
        value.setTags(articles.tags(row.getArticleId()));
        return value;
    }

    /**
     * 逐层检查评论的整条祖先链，只有自身通过、祖先全部通过或占位的评论才计数。
     *
     * @param id 当前文章
     * @return 不含待审、驳回、占位及隐藏后代的真实明细数量，无额外缓存或SQL计数列
     */
    private long commentCount(long id) {
        List<CommentNode> nodes = articles.commentNodes(id);
        Set<Long> visible = CommentVisibility.visible(nodes);
        return nodes.stream().filter(node -> node.getStatus() == 1 && visible.contains(node.getCommentId())).count();
    }

    /**
     * 月份结束时间取下月第一天且不包含它；9999-12 不设上界，避免超出 MySQL 时间范围。
     *
     * @param month 已校验的月份，无过滤时为null
     * @return 下月首日UTC零点，最高月份或无过滤为null
     */
    private LocalDateTime upper(YearMonth month) {
        return month == null || month.equals(YearMonth.of(9999, 12)) ? null : month.plusMonths(1).atDay(1)
                .atStartOfDay();
    }

    /**
     * 时间由服务端按 UTC 毫秒生成，写进 DATETIME(3)，不取机器本地时间或客户端时间。
     *
     * @return 服务端当前UTC时间；仅用于首次发布、回收和公开过滤
     */
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
