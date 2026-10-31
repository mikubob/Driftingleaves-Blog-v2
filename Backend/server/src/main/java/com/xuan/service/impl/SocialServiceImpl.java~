package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.entity.HomeSocialLink;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.SocialMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ISocialService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.SocialPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
/**
 * 展示与维护直接读同一张表，成功审计失败时资料一起回滚。
 */
@Service
@RequiredArgsConstructor
public class SocialServiceImpl extends ServiceImpl<SocialMapper, HomeSocialLink> implements ISocialService {
    private final SocialMapper items;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<SocialPublicVO> publicItems() {
        return items.publicItems().stream().map(row -> new SocialPublicVO(row.getSocialId(), row.getName(), row.getIcon(), row.getUrl(), row.getSortOrder())).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<HomeSocialLink> listItems(DisplayQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<HomeSocialLink> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "home:social", permission = "home:social:create")
    public ResourceVersion<HomeSocialLink> create(SocialWriteDTO request) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        HomeSocialLink row = new HomeSocialLink();
        assign(row, request);
        items.insert(row);
        return profile(items.selectById(row.getSocialId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "home:social", permission = "home:social:update", targetIdArgument = 0)
    public ResourceVersion<HomeSocialLink> update(long id, SocialWriteDTO request, String revision) {
        validate(request);
        HomeSocialLink row = lock(id, "update");
        RevisionUtils.check(profile(row).revision(), revision);
        assign(row, request);
        items.replace(row);
        return profile(items.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "home:social", permission = "home:social:delete", targetIdArgument = 0)
    public void delete(long id, String revision) {
        HomeSocialLink row = lock(id, "delete");
        RevisionUtils.check(profile(row).revision(), revision);
        items.deleteById(id);
    }

    /** 地址只检查和保存，不下载媒体或删除外部对象。 */
    private void validate(SocialWriteDTO request) {

        if (!UrlPolicy.validIcon(request.getIcon())) { throw new BusinessException(400, "VALIDATION_ERROR", "图标须为外部URL或注册图标键"); }
        if (!UrlPolicy.validSocial(request.getUrl())) { throw new BusinessException(400, "VALIDATION_ERROR", "社交地址格式错误"); }
    }

    /** 白名单逐项赋值，NULL也写入数据库。 */
    private void assign(HomeSocialLink row, SocialWriteDTO request) {
        row.setName(request.getName());
        row.setIcon(request.getIcon());
        row.setUrl(request.getUrl());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
    }

    /** 先锁操作者再锁资源，每次等待后重查原Token、启用身份和独立权限。 */
    private HomeSocialLink lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        HomeSocialLink row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 全站资源没有SELF版本，必须由授予本次权限的角色提供ALL。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "home:social:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 先检查权限，再报告不存在。 */
    private HomeSocialLink requireItem(HomeSocialLink row) {
        if (row == null) { throw new BusinessException(404, "NOT_FOUND", "资料不存在"); }
        return row;
    }

    /** 业务版本排除审计时间和公开渲染结果。 */
    private ResourceVersion<HomeSocialLink> profile(HomeSocialLink row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(Arrays.asList(row.getSocialId(), row.getName(), row.getIcon(), row.getUrl(), row.getSortOrder(), row.getStatus()))) + "\"");
    }
}
