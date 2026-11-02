package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SkillCreateDTO;
import com.xuan.dto.SkillWriteDTO;
import com.xuan.entity.CvSkill;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.SkillMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.policy.UrlPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ISkillService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.SkillPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能业务实现，公开展示和后台管理读同一张表，成功日志写入失败时资料一起回滚。
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

    /** 查询公开技能列表。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "skills", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<SkillPublicVO> publicItems(DisplayQueryDTO query) {
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, true)
                .convert(row -> new SkillPublicVO(row.getSkillId(), row.getName(), row.getDescription(), row.getIcon(), row.getSortOrder())));
    }

    /** 分页查询后台技能。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CvSkill> listItems(DisplayQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** 根据id查询技能详情及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<CvSkill> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** 新增技能。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "cv:skill", permission = "cv:skill:create")
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public ResourceVersion<CvSkill> create(SkillCreateDTO request) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        CvSkill row = new CvSkill();
        assign(row, request);
        row.setStatus(request.getStatus());
        items.insert(row);
        return profile(items.selectById(row.getSkillId()));
    }

    /** 根据id更新技能。 */
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

    /** 地址只检查和保存，不下载媒体或删除外部对象。 */
    private void validate(SkillWriteDTO request) {

        if (!UrlPolicy.validIcon(request.getIcon())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "图标须为外部URL或注册图标键");
        }
    }

    /** 填入允许修改的字段，NULL 也要写入数据库。 */
    private void assign(CvSkill row, SkillWriteDTO request) {
        row.setName(request.getName());
        row.setDescription(request.getDescription());
        row.setIcon(request.getIcon());
        row.setSortOrder(request.getSortOrder());
    }

    /** 锁住操作者和目标记录，每次等待结束后重新检查原 Token、账号启用状态和本次操作权限。 */
    private CvSkill lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        CvSkill row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 检查本次操作的 ALL 权限，此类资料全站共用，不支持只操作本人的范围。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "cv:skill:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 检查记录是否存在，调用前必须先通过权限校验。 */
    private CvSkill requireItem(CvSkill row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "资料不存在");
        }
        return row;
    }

    /** 计算资料版本，不包含审计时间和前台生成的展示内容。 */
    private ResourceVersion<CvSkill> profile(CvSkill row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(Arrays.asList(row.getSkillId(), row.getName(), row.getDescription(), row.getIcon(), row.getSortOrder(), row.getStatus()))) + "\"");
    }

    /** 批量删除技能，先加锁并检查全部目标，任一失败则整批回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "cv:skill", permission = "cv:skill:delete")
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            CvSkill row = lock(id, "delete");
            RevisionUtils.check(profile(row).revision(), revision);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            this.items.deleteById(id);
        }
    }

    /** 批量修改状态，先检查全部目标和版本，再仅写状态，新版本按请求顺序返回。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-status", target = "cv:skill", permission = "cv:skill:update")
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status) {
        List<CvSkill> rows = new ArrayList<>();
        for (BatchItemDTO item : BatchUtils.sorted(items)) {
            CvSkill row = lock(IdUtils.parse(item.id()), "update");
            RevisionUtils.check(profile(row).revision(), item.revision());
            rows.add(row);
        }
        Map<Long, String> versions = new HashMap<>();
        for (CvSkill row : rows) {
            if (row.getStatus() != status) {
                this.items.updateStatus(row.getSkillId(), status);
                row.setStatus(status);
            }
            versions.put(row.getSkillId(), profile(row).revision());
        }
        return BatchUtils.versions(items, versions);
    }

}
