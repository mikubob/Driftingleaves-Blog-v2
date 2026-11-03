package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.LinkCreateDTO;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.FriendLinkMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.policy.UrlPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IFriendLinkService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.TokenUtils;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 友链维护结果直接用于公开展示，写入和成功审计一起提交。
 */
@Service
@RequiredArgsConstructor
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkMapper, BlogFriendLink> implements IFriendLinkService {

    private final FriendLinkMapper links;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** 获取展示中的友链，排序相同时按 ID 排，不返回后台状态和时间。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "links", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<LinkPublicVO> publicLinks(PageQueryDTO query) {
        return PageResult.from(links.linkPage(new Page<>(query.page(), query.pageSize()), null, true)
                .convert(row -> new LinkPublicVO(row.getLinkId(), row.getLinkName(), row.getUrl(),
                        row.getAvatar(), row.getDescription(), row.getSortOrder())));
    }

    /** 先检查后台和友链查询权限，再用同样条件查列表和总数。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogFriendLink> listLinks(LinkQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(links.linkPage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** 查询详情及版本，在同一个只读事务中读取，避免资料和版本不一致。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LinkProfileVO detail(long id) {
        requireAdmin("query");
        return profile(requireLink(links.selectById(id)));
    }

    /** 锁住操作者并重新检查权限后创建友链，名称和 URL 允许重复，写日志失败就回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "blog:link", permission = "blog:link:create")
    @CacheEvict(cacheNames = "links", allEntries = true)
    public BlogFriendLink create(LinkCreateDTO request) {
        validateUrls(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        BlogFriendLink row = new BlogFriendLink();
        row.setLinkName(request.getLinkName());
        row.setUrl(request.getUrl());
        row.setAvatar(request.getAvatar());
        row.setDescription(request.getDescription());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
        links.insert(row);
        return links.selectById(row.getLinkId());
    }

    /** 先锁操作者，再锁友链；等待结束后重新检查权限和版本，不能覆盖别人的修改。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:link", permission = "blog:link:update", targetIdArgument = 0)
    @CacheEvict(cacheNames = "links", allEntries = true)
    public LinkProfileVO update(long id, LinkWriteDTO request, String revision) {
        validateUrls(request);
        BlogFriendLink row = lockLink(id, "update");
        checkRevision(row, revision);
        row.setLinkName(request.getLinkName());
        row.setUrl(request.getUrl());
        row.setAvatar(request.getAvatar());
        row.setDescription(request.getDescription());
        row.setSortOrder(request.getSortOrder());
        links.updateLink(row);
        return profile(links.selectById(id));
    }

    /** 两个地址共用现有公开 URL 规则，只校验和保存，不能触发服务器抓取。 */
    private void validateUrls(LinkWriteDTO request) {
        if (!UrlPolicy.valid(request.getUrl())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "友链地址无效，请填写以 http:// 或 https:// 开头的外部地址。");
        }
        if (!UrlPolicy.valid(request.getAvatar())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "友链头像地址无效，请填写以 http:// 或 https:// 开头的外部图片地址。");
        }
    }

    /** 权限先于友链存在性检查，等待友链锁后再查一次，不能沿用旧授权。 */
    private BlogFriendLink lockLink(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogFriendLink row = links.lockById(id);
        requireAdmin(operation);
        return requireLink(row);
    }

    /** SELF 对全站字典也有效，但仍要确认原 Token、账号和两项权限。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, "blog:link:" + operation);
    }

    /** 已经检查权限后再报友链不存在，不把缺权限误报为找不到。 */
    private BlogFriendLink requireLink(BlogFriendLink row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "友链不存在");
        }
        return row;
    }

    /** 持有友链锁时先核对版本；缺失、格式不对、过期分别返回 428、400、412。 */
    private void checkRevision(BlogFriendLink row, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "尚未取得编辑版本，请重新打开详情后再操作。");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "编辑版本无效，请保留当前输入，重新打开详情后再操作。");
        }
        if (!revision.equals(profile(row).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "友链已修改，请重新获取");
        }
    }

    /** 版本只算这些业务字段，更新时间变化不会让没改过的资料变成新版本。 */
    private LinkProfileVO profile(BlogFriendLink row) {
        String value = json.writeValueAsString(Arrays.asList(row.getLinkId(), row.getLinkName(), row.getUrl(),
                row.getAvatar(), row.getDescription(), row.getSortOrder(), row.getStatus()));
        return new LinkProfileVO(row, "\"" + TokenUtils.digest(value) + "\"");
    }

    /** 批量删除友情链接，先加锁并检查全部目标，任一失败则整批回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:link", permission = "blog:link:delete")
    @CacheEvict(cacheNames = "links", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            BlogFriendLink row = lockLink(id, "delete");
            checkRevision(row, revision);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            links.deleteById(id);
        }
    }

    /** 批量修改状态，先检查全部目标和版本，再仅写状态，新版本按请求顺序返回。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-status", target = "blog:link", permission = "blog:link:update")
    @CacheEvict(cacheNames = "links", allEntries = true)
    public BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status) {
        List<BlogFriendLink> rows = new ArrayList<>();
        for (BatchItemDTO item : BatchUtils.sorted(items)) {
            BlogFriendLink row = lockLink(IdUtils.parse(item.id()), "update");
            checkRevision(row, item.revision());
            rows.add(row);
        }
        Map<Long, String> versions = new HashMap<>();
        for (BlogFriendLink row : rows) {
            if (row.getStatus() != status) {
                links.updateStatus(row.getLinkId(), status);
                row.setStatus(status);
            }
            versions.put(row.getLinkId(), profile(row).revision());
        }
        return BatchUtils.versions(items, versions);
    }

}
