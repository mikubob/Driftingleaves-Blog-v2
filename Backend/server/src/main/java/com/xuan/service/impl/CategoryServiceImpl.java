package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.CategoryMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ICategoryService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.ArticlePublicVO.Category;
import com.xuan.vo.CategoryProfileVO;
import com.xuan.vo.CategoryPublicDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * 分类和成功日志一起提交，删分类时由外键把文章的分类置空。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, BlogCategory> implements ICategoryService {

    private final CategoryMapper categories;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** 获取展示中的分类，排序相同时按 ID 排，不返回后台状态和时间。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "categories", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<Category> publicCategories(PageQueryDTO query) {
        return PageResult.from(categories.categoryPage(new Page<>(query.page(), query.pageSize()), null, true)
                .convert(row -> new Category(row.getCategoryId(), row.getCategoryName(), row.getSlug(),
                        row.getDescription(), row.getSortOrder())));
    }

    /** 公开入口不因后台 Token 放宽分类状态，也不返回业务版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "categories", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public CategoryPublicDetailVO publicDetail(String slug) {
        CategoryPublicDetailVO value = categories.publicBySlug(slug, LocalDateTime.now(ZoneOffset.UTC));
        if (value == null) {
            throw new BusinessException(404, "NOT_FOUND", "分类不存在");
        }
        return value;
    }

    /** 先检查后台和分类查询权限，再用同样条件查列表和总数。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogCategory> listCategories(CategoryQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(categories.categoryPage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** 资料和版本在同一个快照里读取，避免两者对不上。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CategoryProfileVO detail(long id) {
        requireAdmin("query");
        return profile(requireCategory(categories.selectById(id)));
    }

    /** 锁住操作者并重新检查权限后创建分类，重名或写日志失败就回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "blog:category", permission = "blog:category:create")
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true)
    })
    public BlogCategory create(CategoryCreateDTO request) {
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        BlogCategory row = new BlogCategory();
        row.setCategoryName(request.getCategoryName());
        row.setSlug(request.getSlug());
        row.setDescription(request.getDescription());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
        categories.insert(row);
        return categories.selectById(row.getCategoryId());
    }

    /** 先锁操作者，再锁分类；等待结束后重新检查权限和版本，不能覆盖别人的修改。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:category", permission = "blog:category:update", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true)
    })
    public CategoryProfileVO update(long id, CategoryUpdateDTO request, String revision) {
        BlogCategory row = lockCategory(id, "update");
        checkRevision(row, revision);
        row.setCategoryName(request.getCategoryName());
        row.setSlug(request.getSlug());
        row.setDescription(request.getDescription());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
        categories.updateCategory(row);
        return profile(categories.selectById(id));
    }

    /** 删分类和文章分类置空在同一事务完成，任何一步或成功日志失败都会回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:category", permission = "blog:category:delete", targetIdArgument = 0)
    @Caching(evict = {
            @CacheEvict(cacheNames = "articles", allEntries = true),
            @CacheEvict(cacheNames = "search", allEntries = true),
            @CacheEvict(cacheNames = "articleDetail", allEntries = true),
            @CacheEvict(cacheNames = "archives", allEntries = true),
            @CacheEvict(cacheNames = "rankings", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "authors", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "rss", allEntries = true),
            @CacheEvict(cacheNames = "sitemap", allEntries = true)
    })
    public void delete(long id, String revision) {
        BlogCategory row = lockCategory(id, "delete");
        checkRevision(row, revision);
        categories.deleteById(id);
    }

    /** 权限先于分类存在性检查，等待分类锁后再查一次，不能沿用旧授权。 */
    private BlogCategory lockCategory(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogCategory row = categories.lockById(id);
        requireAdmin(operation);
        return requireCategory(row);
    }

    /** SELF 对全站字典也有效，但仍要确认原 Token、账号和两项权限。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, "blog:category:" + operation);
    }

    /** 已经检查权限后再报分类不存在，不把缺权限误报为找不到。 */
    private BlogCategory requireCategory(BlogCategory row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "分类不存在");
        }
        return row;
    }

    /** 持有分类锁时先核对版本；缺失、格式不对、过期分别返回 428、400、412。 */
    private void checkRevision(BlogCategory row, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(row).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "分类已修改，请重新获取");
        }
    }

    /** 版本只算这些业务字段，更新时间变化不会让没改过的资料变成新版本。 */
    private CategoryProfileVO profile(BlogCategory row) {
        String value = json.writeValueAsString(Arrays.asList(row.getCategoryId(), row.getCategoryName(), row.getSlug(),
                row.getDescription(), row.getSortOrder(), row.getStatus()));
        return new CategoryProfileVO(row, "\"" + TokenUtils.digest(value) + "\"");
    }
}
