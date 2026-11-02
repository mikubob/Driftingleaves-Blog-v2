package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.MusicWriteDTO;
import com.xuan.entity.BlogMusic;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.MusicMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ResourceVersion;
import com.xuan.policy.MusicAttachmentPolicy;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IMusicService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.FileStore;
import com.xuan.utils.BatchUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.RevisionUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.MusicPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * 展示与维护直接读同一张表，成功审计失败时资料一起回滚。
 */
@Service
@RequiredArgsConstructor
public class MusicServiceImpl extends ServiceImpl<MusicMapper, BlogMusic> implements IMusicService {

    private final MusicMapper items;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;
    private final MusicAttachmentPolicy resources;
    private final FileStore storage;
    private final ArticleMapper articles;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "music", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<MusicPublicVO> publicItems(DisplayQueryDTO query) {
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, true)
                .convert(row -> new MusicPublicVO(row.getMusicId(), row.getTitle(), row.getArtist(),
                        row.getDurationSeconds(), row.getCoverUrl(), row.getAudioUrl(), row.getLyricUrl(),
                        row.getLyricType(), row.getSortOrder())));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogMusic> listItems(DisplayQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<BlogMusic> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "blog:music", permission = "blog:music:create")
    @CacheEvict(cacheNames = "music", allEntries = true)
    public ResourceVersion<BlogMusic> create(MusicWriteDTO request) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("create");
        lockFiles(request);
        requireAdmin("create");
        validateFiles(request);
        requireAdmin("create");
        BlogMusic row = new BlogMusic();
        assign(row, request);
        items.insert(row);
        return profile(items.selectById(row.getMusicId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:music", permission = "blog:music:update", targetIdArgument = 0)
    @CacheEvict(cacheNames = "music", allEntries = true)
    public ResourceVersion<BlogMusic> update(long id, MusicWriteDTO request, String revision) {
        validate(request);
        users.lockById(sessions.currentUserId());
        requireAdmin("update");
        lockFiles(request);
        BlogMusic row = items.lockById(id);
        requireAdmin("update");
        requireItem(row);
        RevisionUtils.check(profile(row).revision(), revision);
        validateFiles(request);
        requireAdmin("update");
        assign(row, request);
        items.replace(row);
        return profile(items.selectById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:music", permission = "blog:music:delete", targetIdArgument = 0)
    @CacheEvict(cacheNames = "music", allEntries = true)
    public void delete(long id, String revision) {
        deleteBatch(List.of(new BatchItemDTO(Long.toString(id), revision)));
    }

    /** 受管文件地址规范化；外部地址只检查和保存，不下载媒体。 */
    private void validate(MusicWriteDTO request) {
        request.setCoverUrl(resources.normalizeUrl(request.getCoverUrl()));
        request.setAudioUrl(resources.normalizeUrl(request.getAudioUrl()));
        request.setLyricUrl(resources.normalizeUrl(request.getLyricUrl()));
    }

    /** 新引用按 ID 升序加锁，始终早于音乐行锁，与文件删除共用锁。 */
    private void lockFiles(MusicWriteDTO request) {
        var ids = new TreeSet<Long>();
        for (String url : Arrays.asList(request.getCoverUrl(), request.getAudioUrl(), request.getLyricUrl())) {
            Long id = resources.fileId(url);
            if (id != null) {
                ids.add(id);
            }
        }
        for (long id : ids) {
            articles.lockFile(id);
        }
    }

    /** 锁后独立复核文件 query 范围、媒体类型和对象可用性，不能借音乐 ALL 扩大文件权限。 */
    private void validateFiles(MusicWriteDTO request) {
        List<String> urls = Arrays.asList(request.getCoverUrl(), request.getAudioUrl(), request.getLyricUrl());
        for (int index = 0; index < urls.size(); index++) {
            Long id = resources.fileId(urls.get(index));
            if (id == null) {
                continue;
            }
            long actor = identities.requireCurrent();
            sessions.requireActive(sessions.currentToken(), actor);
            String scope = authorization.requireScope(actor, "blog:file:query");
            var file = articles.scopedFile(id, "ALL".equals(scope) ? null : actor);
            if (file == null) {
                throw new BusinessException(400, "VALIDATION_ERROR", "引用文件不存在或不在文件权限范围内");
            }
            boolean typeMatches;
            if (index == 0) {
                typeMatches = file.getMimeType().startsWith("image/");
            } else if (index == 1) {
                typeMatches = file.getMimeType().startsWith("audio/");
            } else {
                typeMatches = file.getObjectKey().endsWith("." + request.getLyricType());
            }
            if (!typeMatches) {
                throw new BusinessException(400, "VALIDATION_ERROR", "引用文件类型与音乐字段或歌词格式不一致");
            }
            storage.requireAvailable(file);
        }
    }

    /** 白名单逐项赋值，NULL也写入数据库。 */
    private void assign(BlogMusic row, MusicWriteDTO request) {
        row.setTitle(request.getTitle());
        row.setArtist(request.getArtist());
        row.setDurationSeconds(request.getDurationSeconds());
        row.setCoverUrl(request.getCoverUrl());
        row.setAudioUrl(request.getAudioUrl());
        row.setLyricUrl(request.getLyricUrl());
        row.setLyricType(request.getLyricType());
        row.setSortOrder(request.getSortOrder());
        row.setStatus(request.getStatus());
    }

    /** 先锁操作者再锁资源，每次等待后重查原Token、启用身份和独立权限。 */
    private BlogMusic lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogMusic row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 全站资源没有SELF版本，必须由授予本次权限的角色提供ALL。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:music:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 先检查权限，再报告不存在。 */
    private BlogMusic requireItem(BlogMusic row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "资料不存在");
        }
        return row;
    }

    /** 业务版本排除审计时间和公开渲染结果。 */
    private ResourceVersion<BlogMusic> profile(BlogMusic row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(row.getMusicId(), row.getTitle(), row.getArtist(), row.getDurationSeconds(),
                        row.getCoverUrl(), row.getAudioUrl(), row.getLyricUrl(), row.getLyricType(), row.getSortOrder(),
                        row.getStatus()))) + "\"");
    }

    /** 先锁定并核验全部目标，再在一个事务中清理；任一失败整批回滚。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "batch-delete", target = "blog:music", permission = "blog:music:delete")
    @CacheEvict(cacheNames = "music", allEntries = true)
    public void deleteBatch(List<BatchItemDTO> items) {
        var sorted = BatchUtils.sorted(items);
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            String revision = item.revision();
            BlogMusic row = lock(id, "delete");
            RevisionUtils.check(profile(row).revision(), revision);
        }
        for (var item : sorted) {
            long id = IdUtils.parse(item.id());
            this.items.deleteById(id);
        }
    }

}
