package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.content.ArticleResources;
import com.xuan.dto.FileQueryDTO;
import com.xuan.entity.BlogArticleContent;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.FileMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.FileContent;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IFileService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * 文件和文章共用附件解析与存储，所有写入先锁操作者，文件锁始终早于文章锁。
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, BlogFile> implements IFileService {

    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final FileMapper files;
    private final ArticleMapper articles;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final ArticleResources resources;

    /** 同一快照查询元数据和总数，筛选上传者不能扩大 query 的 SELF。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<FileVO> listFiles(FileQueryDTO query) {
        Long filter = query.uploaderId() == null ? null : IdUtils.parse(query.uploaderId());
        return PageResult.from(files.filePage(new Page<>(query.page(), query.pageSize()), query,
                requireAdmin("query"), filter).convert(this::view));
    }

    /** 元数据不可编辑，无需版本，响应不会泄露内部存储资料。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FileVO detail(long id) {
        return view(requireFile(articles.scopedFile(id, requireAdmin("query"))));
    }

    /** 先临时落盘并验证，再写真实存储和元数据；审计或提交失败同样触发对象补偿。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "upload", target = "blog:file", permission = "blog:file:upload")
    public FileVO upload(MultipartFile upload) {
        users.lockById(sessions.currentUserId());
        requireAdmin("upload");
        String name = upload.getOriginalFilename();
        name = name == null ? "" : name.replaceAll("[/\\\\\\p{Cc}]", "").strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 255 || upload.isEmpty()) {
            throw invalid();
        }
        if (upload.getSize() > MAX_BYTES) {
            throw new BusinessException(413, "PAYLOAD_TOO_LARGE", "图片超过10MiB");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (name.lastIndexOf('.') < 0 || !List.of("png", "jpg", "jpeg").contains(extension)) {
            throw invalid();
        }
        Path temporary = resources.temporary();
        BlogFile row = new BlogFile();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void afterCompletion(int status) {
                resources.cleanup(temporary, status == STATUS_COMMITTED ? null : row);
            }
        });
        try (InputStream input = upload.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new BusinessException(413, "PAYLOAD_TOO_LARGE", "图片超过10MiB");
            }
            Files.write(temporary, bytes);
            validateImage(temporary, extension);
            row.setUploaderId(sessions.currentUserId());
            row.setOriginalName(name);
            row.setMimeType("png".equals(extension) ? "image/png" : "image/jpeg");
            row.setSizeBytes(BigInteger.valueOf(bytes.length));
            resources.store(temporary, extension, row);
            // 图片处理和存储可能耗时，提交前还要确认原 Token 没被撤销。
            requireAdmin("upload");
            files.insert(row);
            return view(files.selectById(row.getFileId()));
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 原文件读取不依赖任何文章，等待文件锁后重新检查原 Token、权限和上传者范围。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FileContent original(long id) {
        requireAdmin("query");
        articles.lockFile(id);
        return content(requireFile(articles.scopedFile(id, requireAdmin("query"))));
    }

    /** 持文件锁扫描全部文章源引用；先删存储，元数据和成功审计随后共同提交。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:file", permission = "blog:file:delete", targetIdArgument = 0)
    public void delete(long id) {
        users.lockById(sessions.currentUserId());
        requireAdmin("delete");
        articles.lockFile(id);
        BlogFile row = requireFile(articles.scopedFile(id, requireAdmin("delete")));
        long after = 0;
        while (true) {
            List<FileMapper.ArticleReference> batch = files.referencesAfter(after);
            if (batch.isEmpty()) {
                break;
            }
            for (FileMapper.ArticleReference article : batch) {
                if (article.getMarkdownContent() == null) {
                    throw unavailable();
                }
                if (resources.inspect(article.getArticleId(), article.getMarkdownContent(), article.getCoverUrl())
                        .fileIds().contains(id)) {
                    throw new BusinessException(409, "RESOURCE_IN_USE", "文件仍被文章引用");
                }
                after = article.getArticleId();
            }
        }
        Long scope = requireAdmin("delete");
        requireFile(articles.scopedFile(id, scope));
        resources.delete(row);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR,
                            "文件对象已删除但元数据未提交，可重试删除完成清理");
                }
            }
        });
        files.deleteScoped(id, scope);
    }

    /** 同一顺序锁文件和文章，锁后查最新 PUBLIC 或两份独立范围，并确认正文/封面确实引用。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FileContent attachment(long articleId, long fileId, boolean admin) {
        Long author = null;
        if (admin) {
            requireAdmin("query");
            author = articleScope();
        }
        articles.lockFile(fileId);
        articles.scoped(articleId, author, true);
        BlogFile file = requireFile(articles.scopedFile(fileId, admin ? requireAdmin("query") : null));
        ArticleRow article = admin ? articles.scoped(articleId, articleScope(), false)
                : articles.publicById(articleId, LocalDateTime.now(ZoneOffset.UTC));
        if (article == null) {
            throw missing();
        }
        BlogArticleContent body = articles.content(articleId);
        if (body == null) {
            throw unavailable();
        }
        if (!resources.inspect(articleId, body.getMarkdownContent(), article.getCoverUrl()).fileIds()
                .contains(fileId)) {
            throw missing();
        }
        return content(file);
    }

    /** 只信 ImageIO 识别结果，先读尺寸限制内存，再实际解码；声明的 Content-Type 不参与。 */
    private void validateImage(Path path, String extension) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!("png".equals(extension) ? "png".equals(format) : "jpeg".equals(format))) {
                    throw invalid();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 25000000) {
                    throw invalid();
                }
                reader.addIIOReadWarningListener((source, warning) -> {
                    throw invalid();
                });
                if (reader.read(0) == null) {
                    throw invalid();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    /** 原 Token 与账号实时有效，ALL/SELF 只取本次文件操作的授权。 */
    private Long requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:file:" + operation)) ? null : actor;
    }

    /** 文章 query 单独计算范围，不能拿文件的 ALL 放大文章 SELF。 */
    private Long articleScope() {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:article:query")) ? null : actor;
    }

    /** 元数据只取白名单字段，不把实体直接交给 HTTP 序列化。 */
    private FileVO view(BlogFile row) {
        return new FileVO(row.getFileId(), row.getUploaderId(), row.getOriginalName(), row.getMimeType(),
                row.getSizeBytes(), row.getStorageType(), row.getCreateTime(),
                "/api/v1/admin/files/" + row.getFileId() + "/content");
    }

    /** 先完成鉴权和范围过滤，再给缺失或越界统一 404。 */
    private BlogFile requireFile(BlogFile row) {
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /** 在持锁事务里读完原图，避免返回延迟流后状态已经变化。 */
    private FileContent content(BlogFile row) {
        return new FileContent(row.getOriginalName(), row.getMimeType(), resources.read(row));
    }

    /** 图片校验失败不回显文件内容、名称或解析器异常。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "文件名称、类型或图片结构不符合要求");
    }

    /** 私密资源和不存在资源共用错误，避免透露其状态。 */
    private BusinessException missing() {
        return new BusinessException(404, "NOT_FOUND", "文件或文章不存在");
    }

    /** 存储故障让事务失败，不能返回空图片成功。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "文件存储暂不可用");
    }
}
