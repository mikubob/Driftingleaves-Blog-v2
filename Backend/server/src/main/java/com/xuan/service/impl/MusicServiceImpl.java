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
 * 音乐业务实现，公开展示和后台管理读同一张表，成功日志写入失败时资料一起回滚。
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

    /** 查询公开音乐列表。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "music", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PageResult<MusicPublicVO> publicItems(DisplayQueryDTO query) {
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, true)
                .convert(row -> new MusicPublicVO(row.getMusicId(), row.getTitle(), row.getArtist(),
                        row.getDurationSeconds(), row.getCoverUrl(), row.getAudioUrl(), row.getLyricUrl(),
                        row.getLyricType(), row.getSortOrder())));
    }

    /** 分页查询后台音乐。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<BlogMusic> listItems(DisplayQueryDTO query) {
        requireAdmin("query");
        return PageResult.from(items.resourcePage(new Page<>(query.page(), query.pageSize()), query, false));
    }

    /** 根据id查询音乐详情及版本。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResourceVersion<BlogMusic> detail(long id) {
        requireAdmin("query");
        return profile(requireItem(items.selectById(id)));
    }

    /** 新增音乐。 */
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

    /** 根据id更新音乐。 */
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

    /** 根据id删除音乐。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:music", permission = "blog:music:delete", targetIdArgument = 0)
    @CacheEvict(cacheNames = "music", allEntries = true)
    public void delete(long id, String revision) {
        deleteBatch(List.of(new BatchItemDTO(Long.toString(id), revision)));
    }

    /** 整理音乐文件地址，外部链接只校验和保存，不下载内容。 */
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

    /** 检查音乐附件，加锁后重新检查文件查询范围、类型和可用性，音乐的 ALL 权限不能扩大文件权限。 */
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

    /** 填入允许修改的字段，NULL 也要写入数据库。 */
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

    /** 锁住操作者和目标记录，每次等待结束后重新检查原 Token、账号启用状态和本次操作权限。 */
    private BlogMusic lock(long id, String operation) {
        users.lockById(sessions.currentUserId());
        requireAdmin(operation);
        BlogMusic row = items.lockById(id);
        requireAdmin(operation);
        return requireItem(row);
    }

    /** 检查本次操作的 ALL 权限，此类资料全站共用，不支持只操作本人的范围。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:music:" + operation))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 检查记录是否存在，调用前必须先通过权限校验。 */
    private BlogMusic requireItem(BlogMusic row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "资料不存在");
        }
        return row;
    }

    /** 计算资料版本，不包含审计时间和前台生成的展示内容。 */
    private ResourceVersion<BlogMusic> profile(BlogMusic row) {
        return new ResourceVersion<>(row, "\"" + TokenUtils.digest(json.writeValueAsString(
                Arrays.asList(row.getMusicId(), row.getTitle(), row.getArtist(), row.getDurationSeconds(),
                        row.getCoverUrl(), row.getAudioUrl(), row.getLyricUrl(), row.getLyricType(), row.getSortOrder(),
                        row.getStatus()))) + "\"");
    }

    /** 批量删除音乐，先加锁并检查全部目标，任一失败则整批回滚。 */
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
