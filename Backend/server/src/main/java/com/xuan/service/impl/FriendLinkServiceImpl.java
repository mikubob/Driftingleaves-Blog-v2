package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.LinkApplicationDTO;
import com.xuan.dto.LinkCreateDTO;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.entity.SysUser;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.FriendLinkMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.LinkApplicationRow;
import com.xuan.policy.UrlPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IFriendLinkService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.AdminLinkVO;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.LinkProfileVO;
import com.xuan.vo.LinkPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 友链维护结果直接用于公开展示，写入和成功审计一起提交。
 */
@Service
@RequiredArgsConstructor
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkMapper, BlogFriendLink> implements IFriendLinkService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DAILY_APPLICATION_LIMIT = 3;

    private final FriendLinkMapper links;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;
    private final Clock clock;

    /**
     * 获取展示中的友链，排序相同时按 ID 排，不返回后台状态和时间。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "links", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<LinkPublicVO> publicLinks(PageQueryDTO query) {
        return PageResult.from(links.publicLinkPage(new Page<>(query.page(), query.pageSize()))
                .convert(row -> new LinkPublicVO(row.getLinkId(), row.getLinkName(), row.getUrl(),
                        row.getAvatar(), row.getDescription(), row.getSortOrder())));
    }

    /**
     * 从当前账号查询申请，各审核状态均可查看。
     */
    @Override
    public BlogFriendLink ownApplication() {
        return links.findApplication(identities.requireCurrent());
    }

    /**
     * 锁住账号后检查申请和当日次数，申请与次数一起提交。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BlogFriendLink submitApplication(LinkApplicationDTO request) {
        // 1. 校验申请站点地址，锁定当前账号并确认无未清理的已有申请。
        validateUrls(request);
        long userId = lockApplicant();
        if (links.findApplication(userId) != null) {
            throw new BusinessException(409, "STATE_CONFLICT", "请先删除已有的友链申请");
        }

        // 2. 检查当日申请次数上限，超限时计算距次日零点的重试秒数。
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(APPLICATION_ZONE));
        LocalDate date = now.toLocalDate();
        Integer savedCount = links.applicationCount(userId, date);
        int used = savedCount == null ? 0 : savedCount;
        if (used >= DAILY_APPLICATION_LIMIT) {
            long nextMidnight = date.plusDays(1).atStartOfDay(APPLICATION_ZONE).toEpochSecond();
            int retryAfter = Math.toIntExact(nextMidnight - now.toEpochSecond());
            throw new BusinessException(429, "RATE_LIMITED", "今天的友链申请次数已用完", retryAfter);
        }

        // 3. 保存待审核友链并递增当日申请计数。
        BlogFriendLink row = new BlogFriendLink();
        setSiteFields(row, request);
        row.setSortOrder(0);
        row.setStatus(0);
        links.insertLink(row, userId, "APPLICATION");
        links.saveApplicationCount(userId, date, used + 1);
        return links.selectById(row.getLinkId());
    }

    /**
     * 按账号串行删除本人申请，成功后清除公开列表缓存。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = "links", allEntries = true)
    public void deleteOwnApplication(long id) {
        long userId = lockApplicant();
        int deleted = links.deleteApplication(id, userId);
        sessions.requireActive(sessions.currentToken(), userId);
        if (deleted == 0) {
            throw new BusinessException(404, "NOT_FOUND", "友链不存在");
        }
    }

    /**
     * 先检查后台和友链查询权限，再用同样条件查列表和总数。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<AdminLinkVO> listLinks(LinkQueryDTO query) {
        requireAdmin("query");
        Long applicantId = IdUtils.parse(query.applicantId());
        return PageResult.from(links.adminLinkPage(new Page<>(query.page(), query.pageSize()), query, applicantId));
    }

    /**
     * 查询详情及版本，在同一个只读事务中读取，避免资料和版本不一致。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LinkProfileVO<AdminLinkVO> detail(long id) {
        requireAdmin("query");
        AdminLinkVO row = requireLink(links.adminLinkDetail(id));
        Long applicantId = row.getApplicant() == null ? null : row.getApplicant().getUserId();
        return new LinkProfileVO<>(row, revision(row, applicantId));
    }

    /**
     * 锁住操作者并重新检查权限后创建友链，名称和 URL 允许重复，写日志失败就回滚。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "blog:link", permission = "blog:link:create")
    @CacheEvict(cacheNames = "links", allEntries = true)
    public BlogFriendLink create(LinkCreateDTO request) {
        validateUrls(request);
        Long applicantId = request.getApplicantId();
        SysUser applicant = lockWriteAccounts("create", applicantId);
        if (applicantId != null) {
            requireAvailableApplicant(applicantId, applicant);
        }
        BlogFriendLink row = new BlogFriendLink();
        setSiteFields(row, request);
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
        links.insertLink(row, applicantId, "ADMIN");
        return links.selectById(row.getLinkId());
    }

    /**
     * 先锁账号再锁友链，检查版本后修改资料和绑定账号。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:link", permission = "blog:link:update", targetIdArgument = 0)
    @CacheEvict(cacheNames = "links", allEntries = true)
    public LinkProfileVO<BlogFriendLink> update(long id, LinkWriteDTO request, String revision) {
        validateUrls(request);
        SysUser applicant = lockWriteAccounts("update", request.getApplicantId());
        LinkApplicationRow row = lockLink(id, "update");
        checkRevision(row, revision);
        Long applicantId = request.hasApplicantId() ? request.getApplicantId() : row.getApplicantId();
        if (applicantId != null && !Objects.equals(applicantId, row.getApplicantId())) {
            requireAvailableApplicant(applicantId, applicant);
        }
        setSiteFields(row, request);
        row.setSortOrder(request.getSortOrder());
        links.updateLink(row, applicantId);
        BlogFriendLink updated = links.selectById(id);
        return new LinkProfileVO<>(updated, revision(updated, applicantId));
    }

    /**
     * 锁住当前账号，等锁后重新检查原会话和账号状态。
     */
    private long lockApplicant() {
        long userId = sessions.currentUserId();
        users.lockById(userId);
        identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), userId);
        return userId;
    }

    /**
     * 填写友链的四个站点资料字段。
     */
    private void setSiteFields(BlogFriendLink row, LinkApplicationDTO request) {
        row.setLinkName(request.getLinkName());
        row.setUrl(request.getUrl());
        row.setAvatar(request.getAvatar());
        row.setDescription(request.getDescription());
    }

    /**
     * 两个地址共用现有公开 URL 规则，只校验和保存，不能触发服务器抓取。
     */
    private void validateUrls(LinkApplicationDTO request) {
        if (!UrlPolicy.valid(request.getUrl())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "友链地址无效，请填写以 http:// 或 https:// 开头的外部地址。");
        }
        if (!UrlPolicy.valid(request.getAvatar())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "友链头像地址无效，请填写以 http:// 或 https:// 开头的外部图片地址。");
        }
    }

    /**
     * 按 ID 升序锁操作者和所选账号，避免与用户管理的加锁顺序相反。
     */
    private SysUser lockWriteAccounts(String operation, Long applicantId) {
        Set<Long> ids = new TreeSet<>();
        ids.add(sessions.currentUserId());
        if (applicantId != null) {
            ids.add(applicantId);
        }
        SysUser applicant = null;
        for (Long id : ids) {
            SysUser user = users.lockById(id);
            if (id.equals(applicantId)) {
                applicant = user;
            }
        }
        requireAdmin(operation);
        return applicant;
    }

    /**
     * 持有账号锁时检查能否绑定，与博客提交共用一条申请的限制。
     */
    private void requireAvailableApplicant(long applicantId, SysUser applicant) {
        if (applicant == null || applicant.getStatus() != 1) {
            throw new BusinessException(400, "VALIDATION_ERROR", "所选用户不存在或已禁用。");
        }
        if (links.findApplication(applicantId) != null) {
            throw new BusinessException(409, "STATE_CONFLICT", "所选用户已有友链申请。");
        }
    }

    /**
     * 等到友链锁后再查权限，不能沿用旧授权。
     */
    private LinkApplicationRow lockLink(long id, String operation) {
        LinkApplicationRow row = links.lockById(id);
        requireAdmin(operation);
        return requireLink(row);
    }

    /**
     * SELF 对全站字典也有效，但仍要确认原 Token、账号和两项权限。
     */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, "blog:link:" + operation);
    }

    /**
     * 已经检查权限后再报友链不存在，不把缺权限误报为找不到。
     */
    private <T extends BlogFriendLink> T requireLink(T row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "友链不存在");
        }
        return row;
    }

    /**
     * 持有友链锁时先核对版本；缺失、格式不对、过期分别返回 428、400、412。
     */
    private void checkRevision(LinkApplicationRow row, String revision) {
        RevisionUtils.check(revision(row, row.getApplicantId()), revision, "友链已修改，请重新获取");
    }

    /**
     * 绑定变更也要检查版本，账号昵称等展示资料不影响版本。
     */
    private String revision(BlogFriendLink row, Long applicantId) {
        String value = json.writeValueAsString(Arrays.asList(row.getLinkId(), row.getLinkName(), row.getUrl(),
                row.getAvatar(), row.getDescription(), row.getSortOrder(), row.getStatus(), applicantId));
        return "\"" + TokenUtils.digest(value) + "\"";
    }

    /**
     * 批量删除友情链接，先加锁并检查全部目标，任一失败则整批回滚。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:link", permission = "blog:link:delete")
    @CacheEvict(cacheNames = "links", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        lockWriteAccounts("delete", null);
        var sorted = BatchUtils.sorted(items);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            LinkApplicationRow row = lockLink(id, "delete");
            checkRevision(row, revision);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            links.deleteById(id);
        }
    }

    /**
     * 批量修改状态，先检查全部目标和版本，再仅写状态，新版本按请求顺序返回。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-status", target = "blog:link", permission = "blog:link:update")
    @CacheEvict(cacheNames = "links", allEntries = true)
    public BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status) {
        lockWriteAccounts("update", null);
        List<LinkApplicationRow> rows = new ArrayList<>();
        for (BatchItemDTO item : BatchUtils.sorted(items)) {
            LinkApplicationRow row = lockLink(IdUtils.parse(item.id()), "update");
            checkRevision(row, item.revision());
            rows.add(row);
        }
        Map<Long, String> versions = new HashMap<>();
        for (LinkApplicationRow row : rows) {
            if (row.getStatus() != status) {
                links.updateStatus(row.getLinkId(), status);
                row.setStatus(status);
            }
            versions.put(row.getLinkId(), revision(row, row.getApplicantId()));
        }
        return BatchUtils.versions(items, versions);
    }

}
