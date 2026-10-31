package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SkillWriteDTO;
import com.xuan.entity.CvSkill;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.SkillMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ISkillService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.SkillPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
/**
 * 展示与维护直接读同一张表，成功审计失败时资料一起回滚。
 */
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, CvSkill> implements ISkillService {
    private final SkillMapper items;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "skills", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<SkillPublicVO> publicItems(DisplayQueryDTO query) {
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, true)
                .convert(row -> new SkillPublicVO(row.getSkillId(), row.getName(), row.getDescription(), row.getIcon(), row.getSortOrder())));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CvSkill> listItems(DisplayQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<CvSkill> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "cv:skill", permission = "cv:skill:create")
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public ResourceVersion<CvSkill> create(SkillWriteDTO request) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        CvSkill row = new CvSkill();
        assign(row, request);
        items.insert(row);
        return profile(items.selectById(row.getSkillId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "cv:skill", permission = "cv:skill:update", targetIdArgument = 0)
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public ResourceVersion<CvSkill> update(long id, SkillWriteDTO request, String revision) {
        validate(request);
        CvSkill row = lock(id, "update");
        RevisionUtils.check(profile(row).revision(), revision);
        assign(row, request);
        items.replace(row);
        return profile(items.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "cv:skill", permission = "cv:skill:delete", targetIdArgument = 0)
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public void delete(long id, String revision) {
        CvSkill row = lock(id, "delete");
        RevisionUtils.check(profile(row).revision(), revision);
        items.deleteById(id);
    }

    /** 地址只检查和保存，不下载媒体或删除外部对象。 */
    private void validate(SkillWriteDTO request) {

        if (!UrlPolicy.validIcon(request.getIcon())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "图标须为外部URL或注册图标键");
        }
    }

    /** 白名单逐项赋值，NULL也写入数据库。 */
    private void assign(CvSkill row, SkillWriteDTO request) {
        row.setName(request.getName());
        row.setDescription(request.getDescription());
        row.setIcon(request.getIcon());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
    }

    /** 先锁操作者再锁资源，每次等待后重查原Token、启用身份和独立权限。 */
    private CvSkill lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        CvSkill row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 全站资源没有SELF版本，必须由授予本次权限的角色提供ALL。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "cv:skill:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 先检查权限，再报告不存在。 */
    private CvSkill requireItem(CvSkill row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "资料不存在");
        }
        return row;
    }

    /** 业务版本排除审计时间和公开渲染结果。 */
    private ResourceVersion<CvSkill> profile(CvSkill row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(Arrays.asList(row.getSkillId(), row.getName(), row.getDescription(), row.getIcon(), row.getSortOrder(), row.getStatus()))) + "\"");
    }
}
