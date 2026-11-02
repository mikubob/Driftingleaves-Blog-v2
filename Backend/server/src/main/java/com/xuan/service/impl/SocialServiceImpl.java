package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.entity.HomeSocialLink;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.SocialMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.policy.UrlPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ISocialService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.SocialPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

/**
 * 社交链接业务实现，公开展示和后台管理读同一张表，成功日志写入失败时资料一起回滚。
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

    /** 查询公开社交链接列表。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "social", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public List<SocialPublicVO> publicItems() {
        return items.publicItems().stream().map(row -> new SocialPublicVO(row.getSocialId(), row.getName(), row.getIcon(), row.getUrl(), row.getSortOrder())).toList();
    }

    /** 分页查询后台社交链接。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<HomeSocialLink> listItems(DisplayQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** 根据id查询社交链接详情及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<HomeSocialLink> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** 新增社交链接。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "home:social", permission = "home:social:create")
    @CacheEvict(cacheNames = "social", allEntries = true)
    public ResourceVersion<HomeSocialLink> create(SocialWriteDTO request) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        HomeSocialLink row = new HomeSocialLink();
        assign(row, request);
        items.insert(row);
        return profile(items.selectById(row.getSocialId()));
    }

    /** 根据id更新社交链接。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "home:social", permission = "home:social:update", targetIdArgument = 0)
    @CacheEvict(cacheNames = "social", allEntries = true)
    public ResourceVersion<HomeSocialLink> update(long id, SocialWriteDTO request, String revision) {
        validate(request);
        HomeSocialLink row = lock(id, "update");
        RevisionUtils.check(profile(row).revision(), revision);
        assign(row, request);
        items.replace(row);
        return profile(items.selectById(id));
    }

    /** 根据id删除社交链接。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "home:social", permission = "home:social:delete", targetIdArgument = 0)
    @CacheEvict(cacheNames = "social", allEntries = true)
    public void delete(long id, String revision) {
        deleteBatch(List.of(new BatchItemDTO(Long.toString(id), revision)));
    }

    /** 地址只检查和保存，不下载媒体或删除外部对象。 */
    private void validate(SocialWriteDTO request) {

        if (!UrlPolicy.validIcon(request.getIcon())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "图标须为外部URL或注册图标键");
        }
        if (!UrlPolicy.validSocial(request.getUrl())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "社交地址格式错误");
        }
    }

    /** 填入允许修改的字段，NULL 也要写入数据库。 */
    private void assign(HomeSocialLink row, SocialWriteDTO request) {
        row.setName(request.getName());
        row.setIcon(request.getIcon());
        row.setUrl(request.getUrl());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
    }

    /** 锁住操作者和目标记录，每次等待结束后重新检查原 Token、账号启用状态和本次操作权限。 */
    private HomeSocialLink lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        HomeSocialLink row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 检查本次操作的 ALL 权限，此类资料全站共用，不支持只操作本人的范围。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "home:social:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 检查记录是否存在，调用前必须先通过权限校验。 */
    private HomeSocialLink requireItem(HomeSocialLink row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "资料不存在");
        }
        return row;
    }

    /** 计算资料版本，不包含审计时间和前台生成的展示内容。 */
    private ResourceVersion<HomeSocialLink> profile(HomeSocialLink row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(Arrays.asList(row.getSocialId(), row.getName(), row.getIcon(), row.getUrl(), row.getSortOrder(), row.getStatus()))) + "\"");
    }

    /** 批量删除社交链接，先加锁并检查全部目标，任一失败则整批回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "home:social", permission = "home:social:delete")
    @CacheEvict(cacheNames = "social", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            HomeSocialLink row = lock(id, "delete");
            RevisionUtils.check(profile(row).revision(), revision);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            this.items.deleteById(id);
        }
    }

}
