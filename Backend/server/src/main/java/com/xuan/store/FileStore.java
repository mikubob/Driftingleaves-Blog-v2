package com.xuan.store;

import com.xuan.client.AliOssClient;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
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
 * 统一读写本地和 OSS 文件，负责路径检查、对象可用性和失败清理。
 */
@Component
@RequiredArgsConstructor
public class FileStore {

    private final FileProperties files;
    private final AliOssProperties ossProperties;
    private final ObjectProvider<AliOssClient> oss;

    /**
     * 文件行锁和权限检查通过后再查实际对象；文件缺失或不可读返回 503，不抓取外部 URL。
     *
     * @param file 数据库元数据，不能使用客户端存储键或Bucket
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

    /** 上传临时文件也放在实际根目录，后面的原子移动才不会跨文件系统。 */
    public Path temporary() {
        try {
            Files.createDirectories(files.getLocalRoot());
            return Files.createTempFile(files.getLocalRoot().toRealPath(), ".upload-", ".tmp");
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 已验证的文件按类型分目录，文件名由服务端随机生成，不使用展示文件名作为路径。 */
    public void store(Path temporary, String extension, BlogFile file) {
        AliOssClient client = oss.getIfAvailable();
        if (client != null) {
            file.setStorageType("oss");
            file.setBucket(ossProperties.getBucket());
            try {
                file.setObjectKey(client.upload(Files.readAllBytes(temporary), extension));
            } catch (IOException exception) {
                throw unavailable();
            }
            return;
        }
        file.setStorageType("local");
        file.setBucket("");
        file.setObjectKey(FileUtils.getFileCategory(extension) + "/" + TokenUtils.random() + "." + extension);
        try {
            Path target = localTarget(file);
            Files.createDirectories(target.getParent());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    /** 在业务锁内读取有界字节，关闭流后才返回，存储缺失或大小变化统一 503。 */
    public byte[] read(BlogFile file) {
        String key = file.getObjectKey();
        String extension = key == null ? "" : key.substring(key.lastIndexOf('.') + 1);
        int limit = FileUtils.maxBytes(extension);
        if (file.getSizeBytes().signum() <= 0 || file.getSizeBytes().compareTo(BigInteger.valueOf(limit)) > 0
                || !file.getMimeType().equals(FileUtils.mimeType(extension))) {
            throw unavailable();
        }
        try (InputStream input = "oss".equals(file.getStorageType())
                ? ossClient(file).open(file.getObjectKey()) : Files.newInputStream(localTarget(file))) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (!file.getSizeBytes().equals(BigInteger.valueOf(bytes.length))) {
                throw unavailable();
            }
            return bytes;
        } catch (IOException | BusinessException exception) {
            throw unavailable();
        }
    }

    /** 已不存在的对象也算清理完成，供数据库提交失败后的 DELETE 重试使用。 */
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

    /** 临时文件和回滚补偿清理失败要留固定运维错误，不能把路径或云端响应写进日志。 */
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

    /** 元数据中的 Bucket 也必须匹配当前受管存储，不能借历史记录访问别的空间。 */
    private AliOssClient ossClient(BlogFile file) {
        AliOssClient client = oss.getIfAvailable();
        if (client == null || !ossProperties.getBucket().equals(file.getBucket())) {
            throw unavailable();
        }
        return client;
    }

    /** 已有文件必须检查真实路径，缺失对象仍检查父目录，保证重试删除不会走出根目录。 */
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

    /** 对象没了或存储出错都要失败，不能拿空对象当作发布成功。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "引用文件存储暂不可用");
    }
}
