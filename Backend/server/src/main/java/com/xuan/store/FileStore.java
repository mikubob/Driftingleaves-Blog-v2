package com.xuan.store;

import com.xuan.client.AliOssClient;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
import com.xuan.model.FileContent;
import com.xuan.properties.AliOssProperties;
import com.xuan.properties.FileProperties;
import com.xuan.utils.FileUtils;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件存储，统一读写本地和 OSS 文件，检查路径、文件可用性并处理失败清理。
 */
@Component
@RequiredArgsConstructor
public class FileStore {

    private final FileProperties files;
    private final AliOssProperties ossProperties;
    private final ObjectProvider<AliOssClient> oss;

    /**
     * 检查文件是否可用，调用前先锁住文件并检查权限；文件缺失或不可读时返回 503，不抓取外部链接。
     *
     * @param file 数据库中的文件资料，不能使用客户端传入的存储路径或 Bucket
     */
    public void requireAvailable(BlogFile file) {
        if ("oss".equals(file.getStorageType())) {
            AliOssClient client = oss.getIfAvailable();
            if (client == null || !file.getBucket().equals(ossProperties.getBucket()) || !client.exists(
                    file.getObjectKey())) {
                throw unavailable();
            }
            return;
        }
        try {
            Path target = localTarget(file);
            if (!Files.isRegularFile(target) || !Files.isReadable(target)
                    || file.getSizeBytes().signum() <= 0 || !file.getSizeBytes()
                    .equals(BigInteger.valueOf(Files.size(target)))) {
                throw unavailable();
            }
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /**
     * 创建上传临时文件，放在存储根目录，保证后续移动在同一个文件系统内完成。
     */
    public Path temporary() {
        try {
            Files.createDirectories(files.getLocalRoot());
            return Files.createTempFile(files.getLocalRoot().toRealPath(), ".upload-", ".tmp");
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /**
     * 保存已校验的文件，按类型分目录，使用服务端随机文件名，不使用展示名称作为路径。
     */
    public void store(Path temporary, String extension, BlogFile file, boolean publicImage) {
        // 1. 启用 OSS 时直接流式上传至私有 Bucket，不把大文件读入堆内存，不返回云端直链。
        AliOssClient client = oss.getIfAvailable();
        if (client != null) {
            file.setStorageType("oss");
            file.setBucket(ossProperties.getBucket());
            try (InputStream input = Files.newInputStream(temporary)) {
                long size = Files.size(temporary);
                file.setObjectKey(client.upload(input, size, extension, publicImage));
            } catch (IOException exception) {
                throw unavailable();
            }
            return;
        }

        // 2. 未启用 OSS 时按类型分目录原子移动到本地存储目录。
        file.setStorageType("local");
        file.setBucket("");
        String category = publicImage ? FileUtils.PUBLIC_IMAGE_CATEGORY : FileUtils.getFileCategory(extension);
        file.setObjectKey(category + "/" + TokenUtils.random() + "." + extension);
        try {
            Path target = localTarget(file);
            Files.createDirectories(target.getParent());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /**
     * 根据服务端保存的目录区分公开图片和普通附件。
     */
    public boolean isPublicImage(BlogFile file) {
        String prefix = "oss".equals(file.getStorageType()) ? ossProperties.getKeyPrefix() : "";
        return file.getObjectKey().startsWith(prefix + FileUtils.PUBLIC_IMAGE_CATEGORY + "/");
    }

    /**
     * 打开文件流描述，不把文件内容整包读入堆内存；文件缺失、超限或大小变化时返回 503。
     *
     * @param file 数据库中的文件元数据
     * @param displayName 对外响应展示的文件名
     * @return 本地路径或待写出层关闭的远端输入流描述
     */
    public FileContent open(BlogFile file, String displayName) {
        String key = file.getObjectKey();
        String extension = key == null ? "" : key.substring(key.lastIndexOf('.') + 1);
        int limit = FileUtils.maxBytes(extension);
        if (file.getSizeBytes() == null || file.getSizeBytes().signum() <= 0
                || file.getSizeBytes().compareTo(BigInteger.valueOf(limit)) > 0
                || !file.getMimeType().equals(FileUtils.mimeType(extension))) {
            throw unavailable();
        }
        long sizeBytes = file.getSizeBytes().longValueExact();
        if ("oss".equals(file.getStorageType())) {
            try {
                InputStream input = ossClient(file).open(file.getObjectKey());
                return FileContent.builder()
                        .name(displayName)
                        .mimeType(file.getMimeType())
                        .sizeBytes(sizeBytes)
                        .stream(input)
                        .build();
            } catch (BusinessException exception) {
                throw unavailable();
            }
        }
        try {
            Path target = localTarget(file);
            if (!Files.isRegularFile(target) || !Files.isReadable(target)
                    || Files.size(target) != sizeBytes) {
                throw unavailable();
            }
            return FileContent.builder()
                    .name(displayName)
                    .mimeType(file.getMimeType())
                    .sizeBytes(sizeBytes)
                    .localPath(target)
                    .build();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /**
     * 删除存储文件，已不存在时也算成功，供数据库提交失败后的删除重试使用。
     */
    public void delete(BlogFile file) {
        if ("oss".equals(file.getStorageType())) {
            ossClient(file).delete(file.getObjectKey());
            return;
        }
        try {
            Files.deleteIfExists(localTarget(file));
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /**
     * 清理临时文件或回滚后残留的文件，失败时记录固定错误，不记录路径或云端响应。
     */
    public void cleanup(Path temporary, BlogFile stored) {
        try {
            if (stored != null && stored.getObjectKey() != null) {
                delete(stored);
            }
        } catch (RuntimeException exception) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "文件补偿清理失败，需要检查孤立对象");
        }
        try {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "上传临时文件清理失败，需要运维清理");
        }
    }

    /**
     * 获取 OSS 客户端，检查文件记录中的 Bucket，不能访问其他存储空间。
     */
    private AliOssClient ossClient(BlogFile file) {
        AliOssClient client = oss.getIfAvailable();
        if (client == null || !ossProperties.getBucket().equals(file.getBucket())) {
            throw unavailable();
        }
        return client;
    }

    /**
     * 获取本地文件路径，文件存在时检查真实路径，不存在时检查父目录，防止访问根目录之外。
     */
    private Path localTarget(BlogFile file) throws IOException {
        if (!"local".equals(file.getStorageType()) || !"".equals(file.getBucket())
                || file.getObjectKey() == null
                || !FileUtils.validObjectKey(file.getObjectKey())) {
            throw unavailable();
        }
        Path root = files.getLocalRoot().toRealPath();
        Path target = root.resolve(file.getObjectKey()).normalize();
        Path parent = target.getParent();
        // 分组目录尚未创建或已被清走时，仍先检查已有上级，保证上传和重试删除不越界。
        while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            parent = parent.getParent();
        }
        if (!target.startsWith(root) || !parent.toRealPath().startsWith(root)
                || Files.isSymbolicLink(target)
                || (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!target.toRealPath().startsWith(root) || !Files.isRegularFile(target)))) {
            throw unavailable();
        }
        return target;
    }

    /**
     * 返回文件存储不可用错误，文件缺失时也不能返回空内容充当成功。
     */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "引用文件存储暂不可用");
    }
}
