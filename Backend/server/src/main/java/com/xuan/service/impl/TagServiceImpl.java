package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.PageQueryDTO;
import com.xuan.dto.TagQueryDTO;
import com.xuan.dto.TagWriteDTO;
import com.xuan.entity.BlogTag;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.TagMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ITagService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.ArticlePublicVO.Tag;
import com.xuan.vo.TagProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 标签字典和成功日志一起提交，删除只清理关联，不删除文章。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, BlogTag> implements ITagService {

    private final TagMapper tags;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** 前台按 ID 升序取标签，不返回创建和修改时间。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Tag> publicTags(PageQueryDTO query) {
        return PageResult.from(tags.tagPage(new Page<>(query.page(), query.pageSize()),
                        new TagQueryDTO(query.page(), query.pageSize(), null))
                .convert(row -> new Tag(row.getTagId(), row.getTagName(), row.getSlug())));
    }

    /** 先检查后台和标签查询权限，再按名称查找，不按当前作者缩小范围。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogTag> listTags(TagQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(tags.tagPage(new Page<>(query.page(), query.pageSize()), query));
    }

    /** 在同一快照里取标签资料和版本，标签不存在就返回 404。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TagProfileVO detail(long id) {
        requireAdmin("query");
        return profile(requireTag(tags.selectById(id)));
    }

    /** 锁住操作者后重新检查权限，只写名称和 slug，时间由数据库填写。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "blog:tag", permission = "blog:tag:create")
    public BlogTag create(TagWriteDTO request) {
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        BlogTag row = new BlogTag();
        row.setTagName(request.getTagName());
        row.setSlug(request.getSlug());
        tags.insert(row);
        return tags.selectById(row.getTagId());
    }

    /** 锁住标签并核对版本后替换资料，原来的文章关联保留。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:tag", permission = "blog:tag:update", targetIdArgument = 0)
    public TagProfileVO update(long id, TagWriteDTO request, String revision) {
        BlogTag row = lockTag(id, "update");
        checkRevision(row, revision);
        row.setTagName(request.getTagName());
        row.setSlug(request.getSlug());
        tags.updateById(row);
        return profile(tags.selectById(id));
    }

    /** 删除标签时由外键一起清掉关联，文章下次计算版本就会发现标签集合已变。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:tag", permission = "blog:tag:delete", targetIdArgument = 0)
    public void delete(long id, String revision) {
        BlogTag row = lockTag(id, "delete");
        checkRevision(row, revision);
        tags.deleteById(id);
    }

    /** 先锁操作者、检查权限，再锁标签；等待结束后还要重新检查权限。 */
    private BlogTag lockTag(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogTag row = tags.lockById(id);
        requireAdmin(operation);
        return requireTag(row);
    }

    /** 字典没有“本人标签”，SELF 也允许操作，但后台入口和业务权限缺一不可。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, "blog:tag:" + operation);
    }

    /** 权限检查通过后，再把没查到的标签报成 404。 */
    private BlogTag requireTag(BlogTag row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "标签不存在");
        }
        return row;
    }

    /** 在标签锁内检查单个带引号的版本，不能用通配符跳过检查。 */
    private void checkRevision(BlogTag row, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(row).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "标签已修改，请重新获取");
        }
    }

    /** 版本只包含 ID、名称和 slug，排除数据库维护的时间。 */
    private TagProfileVO profile(BlogTag row) {
        String value = json.writeValueAsString(List.of(row.getTagId(), row.getTagName(), row.getSlug()));
        return new TagProfileVO(row, "\"" + TokenUtils.digest(value) + "\"");
    }
}
