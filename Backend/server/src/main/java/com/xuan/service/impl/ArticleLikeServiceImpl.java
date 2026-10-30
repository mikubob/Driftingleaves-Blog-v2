package com.xuan.service.impl;

import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleLikeMapper;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IArticleLikeService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.vo.LikeStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 点赞只写 MySQL 明细，文章锁让它与发布、下架、回收互斥，不另存缓存计数。
 */
@Service
@RequiredArgsConstructor
public class ArticleLikeServiceImpl implements IArticleLikeService {

    private final ArticleLikeMapper likes;
    private final ArticleMapper articles;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;

    /** 同一快照里查公开文章与本人状态，不要求 like 权限。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LikeStateVO state(long article) {
        long actor = requireCurrent(false);
        requirePublic(article);
        return likes.state(article, actor);
    }

    /** 拿文章锁后才看复合键，不吞唯一键异常继续事务。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeStateVO like(long article) {
        long actor = lockArticle(article);
        if (!likes.state(article, actor).liked()) {
            likes.add(article, actor);
        }
        return likes.state(article, actor);
    }

    /** 没有明细也返回 false；非公开文章在删除之前就拒绝，既有明细保留。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeStateVO unlike(long article) {
        long actor = lockArticle(article);
        likes.remove(article, actor);
        return likes.state(article, actor);
    }

    /**
     * 先锁用户再锁文章，等待结束后重新确认点赞权限与文章公开状态。
     */
    private long lockArticle(long article) {
        long actor = requireCurrent(true);
        users.lockById(actor);
        articles.scoped(article, null, true);
        // 锁等待期间 Token、权限和公开状态都可能变化，拿到锁后不能沿用旧结论。
        requireCurrent(true);
        requirePublic(article);
        return actor;
    }

    /**
     * 检查原 Token 与启用账号，写操作还要有点赞权限。
     */
    private long requireCurrent(boolean write) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        if (write) {
            authorization.requireScope(actor, "blog:article:like");
        }
        return actor;
    }

    /**
     * 只允许对公开文章读取或修改点赞，其他状态统一返回 404。
     */
    private void requirePublic(long article) {
        if (articles.publicById(article, LocalDateTime.now(ZoneOffset.UTC)) == null) {
            throw new BusinessException(404, "NOT_FOUND", "文章不存在");
        }
    }
}
