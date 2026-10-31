package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.dto.ExperienceWriteDTO;
import com.xuan.entity.CvExperience;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ExperienceMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IExperienceService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.MarkdownUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.ExperiencePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/**
 * 展示与维护直接读同一张表，成功审计失败时资料一起回滚。
 */
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl extends ServiceImpl<ExperienceMapper, CvExperience> implements IExperienceService {

    private final ExperienceMapper items;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "experiences", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<ExperiencePublicVO> publicItems(ExperienceQueryDTO query) {
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, true)
                .convert(row -> new ExperiencePublicVO(row.getExperienceId(), row.getExperienceType(), row.getTitle(),
                        row.getSubtitle(), row.getLogoUrl(), row.getStartDate(), row.getEndDate(), row.getSortOrder(),
                        MarkdownUtils.toHtml(row.getContent()))));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CvExperience> listItems(ExperienceQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<CvExperience> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "cv:experience", permission = "cv:experience:create")
    @CacheEvict(cacheNames = "experiences", allEntries = true)
    public ResourceVersion<CvExperience> create(ExperienceWriteDTO request) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        CvExperience row = new CvExperience();
        assign(row, request);
        items.insert(row);
        return profile(items.selectById(row.getExperienceId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "cv:experience", permission = "cv:experience:update", targetIdArgument = 0)
    @CacheEvict(cacheNames = "experiences", allEntries = true)
    public ResourceVersion<CvExperience> update(long id, ExperienceWriteDTO request, String revision) {
        validate(request);
        CvExperience row = lock(id, "update");
        RevisionUtils.check(profile(row).revision(), revision);
        assign(row, request);
        items.replace(row);
        return profile(items.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "cv:experience", permission = "cv:experience:delete", targetIdArgument = 0)
    @CacheEvict(cacheNames = "experiences", allEntries = true)
    public void delete(long id, String revision) {
        CvExperience row = lock(id, "delete");
        RevisionUtils.check(profile(row).revision(), revision);
        items.deleteById(id);
    }

    /** 地址只检查和保存，不下载媒体或删除外部对象。 */
    private void validate(ExperienceWriteDTO request) {
        if (!UrlPolicy.valid(request.getLogoUrl())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "媒体地址须为外部HTTP(S)资源");
        }
    }

    /** 白名单逐项赋值，NULL也写入数据库。 */
    private void assign(CvExperience row, ExperienceWriteDTO request) {
        row.setExperienceType(request.getExperienceType());
        row.setTitle(request.getTitle());
        row.setSubtitle(request.getSubtitle());
        row.setLogoUrl(request.getLogoUrl());
        row.setContent(request.getContent());
        row.setStartDate(request.getStartDate());
        row.setEndDate(request.getEndDate());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
    }

    /** 先锁操作者再锁资源，每次等待后重查原Token、启用身份和独立权限。 */
    private CvExperience lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        CvExperience row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 全站资源没有SELF版本，必须由授予本次权限的角色提供ALL。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "cv:experience:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 先检查权限，再报告不存在。 */
    private CvExperience requireItem(CvExperience row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "资料不存在");
        }
        return row;
    }

    /** 业务版本排除审计时间和公开渲染结果。 */
    private ResourceVersion<CvExperience> profile(CvExperience row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(row.getExperienceId(), row.getExperienceType(), row.getTitle(), row.getSubtitle(),
                        row.getLogoUrl(), row.getContent(), row.getStartDate(), row.getEndDate(), row.getSortOrder(),
                        row.getStatus()))) + "\"");
    }
}
