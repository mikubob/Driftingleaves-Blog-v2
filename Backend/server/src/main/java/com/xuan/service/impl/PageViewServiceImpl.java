package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.PageViewQueryDTO;
import com.xuan.entity.BlogPageView;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.PageViewMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IPageViewService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.utils.QueryTimeUtils;
import com.xuan.utils.SitePathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 明细与文章累计阅读独立，清理不会修改文章。
 */
@Service
@RequiredArgsConstructor
public class PageViewServiceImpl extends ServiceImpl<PageViewMapper, BlogPageView> implements IPageViewService {

    private final PageViewMapper views;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogPageView> listViews(PageViewQueryDTO query) {
        requireAdmin("query");
        LocalDateTime begin = QueryTimeUtils.parse(query.begin());
        LocalDateTime end = QueryTimeUtils.parse(query.end());
        QueryTimeUtils.requireRange(begin, end);
        if (query.pagePath() != null && !query.pagePath().equals(SitePathUtils.path(query.pagePath()))) {
            throw new BusinessException(400, "VALIDATION_ERROR", "查询路径须为不含参数的规范路径");
        }
        return PageResult.from(
                views.viewPage(new Page<>(query.page(), query.pageSize()), query, IdUtils.parse(query.visitorId()),
                        IdUtils.parse(query.userId()), IdUtils.parse(query.articleId()), begin, end));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:view", permission = "blog:view:delete", targetIdArgument = 0)
    @CacheEvict(cacheNames = "statistics", allEntries = true)
    public void delete(long id) {
        users.lockById(sessions.currentUserId());
        requireAdmin("delete");
        BlogPageView row = views.lockById(id);
        requireAdmin("delete");
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "浏览记录不存在");
        }
        views.deleteById(id);
    }

    /** 查询与清理分别使用自己的ALL，不能借另一项权限扩大范围。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:view:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }
}
