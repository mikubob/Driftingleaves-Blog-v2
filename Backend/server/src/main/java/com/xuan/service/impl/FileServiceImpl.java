package com.xuan.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.FileQueryDTO;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.FileMapper;
import com.xuan.mapper.MusicMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.model.ArticleRow;
import com.xuan.model.FileContent;
import com.xuan.policy.FileContentPolicy;
import com.xuan.policy.FileUploadUsagePolicy;
import com.xuan.policy.MusicAttachmentPolicy;
import com.xuan.properties.ImageProperties;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IFileService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.FileStore;
import com.xuan.utils.FileUtils;
import com.xuan.utils.IdUtils;
import com.xuan.utils.ImageCompressUtils;
import com.xuan.vo.BatchFileResultVO;
import com.xuan.vo.BatchFileResultsVO;
import com.xuan.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 文件存储独立处理，文章和音乐分别检查引用；所有写入先锁操作者，文件锁始终早于文章锁。
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, BlogFile> implements IFileService {

    private final ObjectProvider<IFileService> fileService;

    private final FileMapper files;
    private final ArticleMapper articles;
    private final MusicMapper music;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final FileContentPolicy contentPolicy;
    private final MusicAttachmentPolicy musicResources;
    private final FileStore storage;
    private final ImageProperties imageProperties;
    private final PublicSiteProperties publicSiteProperties;
    private final PlatformTransactionManager transactionManager;

    /**
     * 分页查询文件，在同一个只读事务中查询文件资料和总数，筛选上传者不能扩大查询权限的 SELF 范围。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<FileVO> listFiles(FileQueryDTO query) {
        Long filter = query.getUploaderId() == null ? null : IdUtils.parse(query.getUploaderId());
        return PageResult.from(files.filePage(new Page<>(query.getPage(), query.getPageSize()), query,
                requireAdmin("query"), filter).convert(this::view));
    }

    /**
     * 根据id查询文件详情，不返回编辑版本或存储目录。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FileVO detail(long id) {
        return view(requireFile(articles.scopedFile(id, requireAdmin("query"))));
    }

    /**
     * 上传公开展示图片，复用文件上传校验与事务，成功后拼接公开访问地址返回。
     *
     * @param file 上传图片文件
     * @param usage 使用场景标识
     * @return 公开展示图片访问地址
     */
    @Override
    public URI uploadDisplayImage(MultipartFile file, String usage) {
        URI publicApi = publicSiteProperties.base("api");
        FileVO value = fileService.getObject().upload(file, true, usage);
        return URI.create(publicApi + "/blog/display-images/" + value.getFileId());
    }

    /**
     * 上传文件，在事务外流式写入临时文件、校验内容并上传存储，最后进入短写事务落库；任一步失败清理新文件。
     */
    @Override
    public FileVO upload(MultipartFile upload, boolean publicImage, String usage) {
        // 1. 提前检查上传权限、文件名、扩展名及文件大小上限，避免无效大文件占用磁盘与带宽。
        requireAdmin("upload");
        String name = upload.getOriginalFilename();
        name = name == null ? "" : name.replaceAll("[/\\\\\\p{Cc}]", "").strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 255 || upload.isEmpty()) {
            throw new BusinessException(400, "VALIDATION_ERROR", "文件不能为空，文件名须为 1 至 255 个字符，请重新选择文件。");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        int limit = FileUtils.maxBytes(extension);
        if (name.lastIndexOf('.') < 0 || limit == 0) {
            throw new BusinessException(400, "VALIDATION_ERROR", "不支持此文件类型，请选择 PNG/JPEG 图片、MP3/WAV/OGG/FLAC 音频或 LRC/JSON/TXT 歌词。");
        }
        String mime = FileUtils.mimeType(extension);
        if (publicImage && !mime.startsWith("image/")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "请选择 PNG 或 JPEG 图片。");
        }
        if (upload.getSize() > limit) {
            throw tooLarge();
        }

        // 2. 在数据库事务外流式落盘、按类型校验并上传存储，避免行锁与数据库连接被存储 I/O 占用。
        Path temporary = storage.temporary();
        BlogFile row = new BlogFile();
        boolean committed = false;
        try {
            long sizeBytes = copyBounded(upload, temporary, limit);
            if (mime.startsWith("image/")) {
                BufferedImage image = contentPolicy.validateImage(temporary, extension);
                byte[] originalBytes = Files.readAllBytes(temporary);
                byte[] compressed = ImageCompressUtils.compress(originalBytes, image, extension, imageProperties);
                if (imageProperties.isEnabled()) {
                    extension = imageProperties.getOutputFormat().toLowerCase(Locale.ROOT);
                    mime = FileUtils.mimeType(extension);
                }
                if (compressed != originalBytes) {
                    Files.write(temporary, compressed);
                    sizeBytes = compressed.length;
                }
            } else if (mime.startsWith("audio/")) {
                contentPolicy.validateAudio(temporary, sizeBytes, extension);
            } else {
                contentPolicy.validateLyrics(Files.readAllBytes(temporary), extension);
            }

            row.setUploaderId(sessions.currentUserId());
            row.setOriginalName(name);
            row.setMimeType(mime);
            row.setUploadUsage(FileUploadUsagePolicy.resolve(usage, mime));
            row.setSizeBytes(BigInteger.valueOf(sizeBytes));
            storage.store(temporary, extension, row, publicImage);

            // 3. 通过代理进入短写事务复核权限、写入元数据并记录成功审计日志。
            FileVO result = fileService.getObject().commitUpload(upload, row);
            committed = true;
            return result;
        } catch (IOException exception) {
            throw unavailable();
        } finally {
            storage.cleanup(temporary, committed ? null : row);
        }
    }

    /**
     * 在短写事务内锁住操作者、复核上传权限并保存文件元数据，同时由切面记录操作审计日志。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "upload", target = "blog:file", permission = "blog:file:upload")
    public FileVO commitUpload(MultipartFile upload, BlogFile row) {
        users.lockById(sessions.currentUserId());
        // 内容处理和存储可能耗时，提交前还要确认原 Token 与上传权限未被撤销。
        requireAdmin("upload");
        files.insert(row);
        return view(files.selectById(row.getFileId()));
    }

    /**
     * 原文件读取不依赖任何文章，在短只读事务内检查原 Token、权限和上传者范围，释放连接后再流式打开存储。
     */
    @Override
    public FileContent original(long id) {
        BlogFile file = readOnly(() -> requireFile(articles.scopedFile(id, requireAdmin("query"))));
        return content(file);
    }

    /**
     * 只读取专门公开上传的图片，使用固定文件名避免公开原始文件名，在只读事务外流式打开存储。
     */
    @Override
    public FileContent displayImage(long id) {
        BlogFile file = readOnly(() -> requireFile(articles.scopedFile(id, null)));
        if (!storage.isPublicImage(file)) {
            throw missing();
        }
        String name = switch (file.getMimeType()) {
            case "image/webp" -> "image.webp";
            case "image/png" -> "image.png";
            default -> "image.jpg";
        };
        return storage.open(file, name);
    }

    /**
     * 删除文件，锁住文件后通过索引检查音乐与文章引用；先删除存储文件，再一起提交文件记录删除和成功日志。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "delete", target = "blog:file", permission = "blog:file:delete", targetIdArgument = 0)
    public void delete(long id) {
        // 1. 锁住操作者和目标文件，复核删除权限与范围。
        users.lockById(sessions.currentUserId());
        requireAdmin("delete");
        articles.lockFile(id);
        BlogFile row = requireFile(articles.scopedFile(id, requireAdmin("delete")));

        // 2. 检查音乐引用与 blog_article_file 关联表索引，仍被引用时拒绝删除。
        if (music.hasFileReference(musicResources.fileUrl(id))) {
            throw new BusinessException(409, "RESOURCE_IN_USE", "文件仍被音乐引用");
        }
        if (files.hasArticleReference(id)) {
            throw new BusinessException(409, "RESOURCE_IN_USE", "文件仍被文章引用");
        }

        // 3. 再次确认权限后先删存储对象，并登记未提交时的告警日志。
        Long scope = requireAdmin("delete");
        requireFile(articles.scopedFile(id, scope));
        storage.delete(row);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 文件已删除但数据库未提交时，记录清理失败，供后续重试。
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

    /**
     * 读取文章附件，在短只读事务内检查公开状态或文章、文件权限范围及引用表记录，释放连接后再流式打开存储。
     */
    @Override
    public FileContent attachment(long articleId, long fileId, boolean admin) {
        BlogFile file = readOnly(() -> {
            BlogFile candidate = requireFile(articles.scopedFile(fileId, admin ? requireAdmin("query") : null));
            ArticleRow article = admin ? articles.scoped(articleId, articleScope(), false)
                    : articles.publicById(articleId, LocalDateTime.now(ZoneOffset.UTC));
            if (article == null) {
                throw missing();
            }
            if (!files.hasArticleFileReference(articleId, fileId)) {
                throw missing();
            }
            return candidate;
        });
        return content(file);
    }

    /**
     * 隐藏曲目不能通过文件地址匿名读取；短只读事务确认公开曲目引用后再流式打开存储。
     */
    @Override
    public FileContent musicAttachment(long fileId) {
        BlogFile file = readOnly(() -> {
            BlogFile candidate = requireFile(articles.scopedFile(fileId, null));
            if (!music.hasPublicFileReference(musicResources.fileUrl(fileId))) {
                throw missing();
            }
            return candidate;
        });
        return content(file);
    }

    /**
     * 有界流式复制上传文件到临时路径，边读边校验字节上限，避免 60 MiB 音频整包入堆。
     */
    private long copyBounded(MultipartFile upload, Path temporary, int limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        try (InputStream input = upload.getInputStream();
                OutputStream output = Files.newOutputStream(temporary)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw tooLarge();
                }
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw contentPolicy.invalid();
        }
        return total;
    }

    /**
     * 检查文件操作权限，原 Token 和账号必须仍有效，ALL/SELF 只使用本次操作的授权。
     */
    private Long requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:file:" + operation)) ? null : actor;
    }

    /**
     * 检查文章查询范围，文件的 ALL 权限不能扩大文章的 SELF 范围。
     */
    private Long articleScope() {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        return "ALL".equals(authorization.requireScope(actor, "blog:article:query")) ? null : actor;
    }

    /**
     * 整理文件响应，返回上传时保存的用途。
     */
    private FileVO view(BlogFile row) {
        String objectKey = row.getObjectKey();
        String storedName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        String usage = row.getUploadUsage();
        return FileVO.builder()
                .fileId(row.getFileId())
                .uploaderId(row.getUploaderId())
                .originalName(row.getOriginalName())
                .storedName(storedName)
                .mimeType(row.getMimeType())
                .sizeBytes(row.getSizeBytes())
                .storageType(row.getStorageType())
                .createTime(row.getCreateTime())
                .previewUrl("/api/v1/admin/files/" + row.getFileId() + "/content")
                .publicImage(storage.isPublicImage(row))
                .usage(usage)
                .usages(List.of(usage))
                .build();
    }

    /**
     * 检查文件是否存在，先校验权限和范围；文件不存在或无权访问时都返回 404。
     */
    private BlogFile requireFile(BlogFile row) {
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /**
     * 在短只读事务内执行权限与引用校验，提交释放数据库连接后再流式打开存储。
     */
    private <T> T readOnly(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> action.get());
    }

    /**
     * 在事务外流式打开存储文件，返回本地路径或远端输入流描述。
     */
    private FileContent content(BlogFile row) {
        return storage.open(row, row.getOriginalName());
    }

    /**
     * 总请求上限之外，每种类型仍执行自己的字节上限。
     */
    private BusinessException tooLarge() {
        return new BusinessException(413, "PAYLOAD_TOO_LARGE", "图片限10MiB、音频限60MiB、歌词限1MiB");
    }

    /**
     * 私密资源和不存在资源共用错误，避免透露其状态。
     */
    private BusinessException missing() {
        return new BusinessException(404, "NOT_FOUND", "文件或引用内容不存在");
    }

    /**
     * 存储故障让事务失败，不能返回空图片成功。
     */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "文件存储暂不可用");
    }

    /**
     * 批量删除文件，存储无法整批回滚，每个文件通过服务代理单独提交，并返回实际结果。
     */
    @Override
    public BatchFileResultsVO deleteBatch(List<String> ids) {
        requireAdmin("delete");
        var results = new ArrayList<BatchFileResultVO>();
        BusinessException stopped = null;
        for (String value : ids) {
            long id = IdUtils.parse(value);
            try {
                if (stopped != null) {
                    throw stopped;
                }
                fileService.getObject().delete(id);
                results.add(BatchFileResultVO.builder()
                        .id(id)
                        .httpStatus(200)
                        .code("OK")
                        .msg("成功")
                        .build());
            } catch (BusinessException error) {
                results.add(BatchFileResultVO.builder()
                        .id(id)
                        .httpStatus(error.getStatus())
                        .code(error.getCode())
                        .msg(error.getMessage())
                        .build());
                if (error.getStatus() == 401 || error.getStatus() == 403) {
                    stopped = error;
                }
            } catch (NotLoginException error) {
                stopped = new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
                results.add(BatchFileResultVO.builder()
                        .id(id)
                        .httpStatus(401)
                        .code(stopped.getCode())
                        .msg(stopped.getMessage())
                        .build());
            } catch (DataAccessException | TransactionException error) {
                results.add(BatchFileResultVO.builder()
                        .id(id)
                        .httpStatus(503)
                        .code("SERVICE_UNAVAILABLE")
                        .msg("文件服务暂不可用")
                        .build());
            } catch (RuntimeException error) {
                results.add(BatchFileResultVO.builder()
                        .id(id)
                        .httpStatus(500)
                        .code("INTERNAL_ERROR")
                        .msg("文件清理失败")
                        .build());
            }
        }
        return BatchFileResultsVO.builder().results(results).build();
    }

}
