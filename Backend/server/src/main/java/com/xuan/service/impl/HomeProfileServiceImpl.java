package com.xuan.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.HomeProfileWriteDTO;
import com.xuan.entity.HomeProfile;
import com.xuan.entity.SysUser;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.HomeProfileMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IHomeProfileService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.HomeProfilePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * 展示联系信息独立维护，不能从登录邮箱自动补全。
 */
@Service
@RequiredArgsConstructor
public class HomeProfileServiceImpl extends ServiceImpl<HomeProfileMapper, HomeProfile> implements IHomeProfileService {

    private final HomeProfileMapper profiles;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "home", key = "@publicCacheKeys.key(#root.methodName, #root.args)", unless = "#result == null")
    public HomeProfilePublicVO publicProfile() {
        HomeProfile row = profiles.selectById(1L);
        return row == null ? null : new HomeProfilePublicVO(row.getNickname(), row.getTagline(), row.getDescription(),
                row.getAvatar(), row.getWebsite(), row.getPublicEmail(), row.getGithubUrl(), row.getLocation());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<HomeProfile> detail() {
        requireAdmin("query");
        return profile(profiles.selectById(1L));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "home:profile", permission = "home:profile:update")
    @CacheEvict(cacheNames = "home", allEntries = true)
    public ResourceVersion<HomeProfile> replace(HomeProfileWriteDTO request, String revision) {
        if (!UrlPolicy.valid(request.getAvatar()) || !UrlPolicy.valid(request.getWebsite()) || !UrlPolicy.valid(
                request.getGithubUrl())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "展示地址须为外部HTTP(S)资源");
        }
        // 结构检查点也串行首次设置，随后按ID锁操作者和所选账号。
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "管理保护角色未初始化");
        }
        requireAdmin("update");
        Set<Long> ids = new TreeSet<>();
        ids.add(sessions.currentUserId());
        ids.add(request.getUserId());
        SysUser owner = null;
        for (long id : ids) {
            SysUser user = users.lockById(id);
            requireAdmin("update");
            if (id == request.getUserId()) {
                owner = user;
            }
        }
        HomeProfile row = profiles.lockProfile();
        requireAdmin("update");
        RevisionUtils.check(profile(row).revision(), revision);
        if (owner == null || owner.getStatus() != 1) {
            throw new BusinessException(400, "VALIDATION_ERROR", "站点主人须为启用账号");
        }
        boolean create = row == null;
        if (create) {
            row = new HomeProfile();
            row.setProfileId(1L);
        }
        row.setUserId(request.getUserId());
        row.setNickname(request.getNickname());
        row.setTagline(request.getTagline());
        row.setDescription(request.getDescription());
        row.setAvatar(request.getAvatar());
        row.setWebsite(request.getWebsite());
        row.setPublicEmail(request.getPublicEmail());
        row.setGithubUrl(request.getGithubUrl());
        row.setLocation(request.getLocation());
        if (create) {
            profiles.insert(row);
        } else {
            profiles.replace(row);
        }
        return profile(profiles.selectById(1L));
    }

    /** 锁等待后重新检查原会话、启用身份和本次操作的ALL。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "home:profile:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** NULL有独立版本，不把数据库时间或账号资料放进版本。 */
    private ResourceVersion<HomeProfile> profile(HomeProfile row) {
        Object value = row == null ? null : Arrays.asList(row.getUserId(), row.getNickname(), row.getTagline(),
                row.getDescription(), row.getAvatar(), row.getWebsite(), row.getPublicEmail(), row.getGithubUrl(),
                row.getLocation());
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(value)) + "\"");
    }
}
