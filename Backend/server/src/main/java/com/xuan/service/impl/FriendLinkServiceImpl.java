package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.FriendLinkMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IFriendLinkService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.LinkProfileVO;
import com.xuan.vo.LinkPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

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

    /** 资料和版本在同一个快照里读取，避免两者对不上。 */
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
    public BlogFriendLink create(LinkWriteDTO request) {
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
    public LinkProfileVO update(long id, LinkWriteDTO request, String revision) {
        validateUrls(request);
        BlogFriendLink row = lockLink(id, "update");
        checkRevision(row, revision);
        row.setLinkName(request.getLinkName());
        row.setUrl(request.getUrl());
        row.setAvatar(request.getAvatar());
        row.setDescription(request.getDescription());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
        links.updateLink(row);
        return profile(links.selectById(id));
    }

    /** 版本通过后物理删除，成功日志失败时删除也会回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:link", permission = "blog:link:delete", targetIdArgument = 0)
    public void delete(long id, String revision) {
        BlogFriendLink row = lockLink(id, "delete");
        checkRevision(row, revision);
        links.deleteById(id);
    }

    /** 两个地址共用现有公开 URL 规则，只校验和保存，不能触发服务器抓取。 */
    private void validateUrls(LinkWriteDTO request) {
        if (!UrlPolicy.valid(request.getUrl()) || !UrlPolicy.valid(request.getAvatar())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "url和avatar须为合法的外部HTTP(S)地址");
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
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
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
}
