package com.xuan.client;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AliOssProperties;
import com.xuan.utils.FileUtils;
import com.xuan.utils.TokenUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * OSS 文件客户端，文件名由服务端生成，只读写指定前缀，不提供公开直链。
 */
public class AliOssClient {

    private final OSS oss;
    private final String bucket;
    private final String prefix;

    /** 初始化 OSS 文件客户端，不保留明文凭据，SDK 由容器关闭。 */
    public AliOssClient(OSS oss, AliOssProperties properties) {
        properties.validateEnabled();
        this.oss = oss;
        this.bucket = properties.getBucket();
        this.prefix = properties.getKeyPrefix();
    }

    /**
     * 上传文件到 OSS，文件类型、归属和数据库失败后的清理由业务层负责。
     *
     * @param bytes 非空且已按类型限长的文件内容
     * @param extension 已与内容识别一致的受支持扩展名，不使用原文件名
     * @return 专属前缀/分类目录/随机文件名，只有确认上传成功才返回
     */
    public String upload(byte[] bytes, String extension) {
        if (bytes == null || bytes.length == 0 || bytes.length > FileUtils.maxBytes(extension)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "文件大小或扩展名不符合要求");
        }
        String key = prefix + FileUtils.getFileCategory(extension) + "/" + TokenUtils.random() + "." + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(FileUtils.mimeType(extension));
        // 新对象一律设为私有，避免继承公开读取设置；不改 Bucket 或已有对象的 ACL。
        metadata.setObjectAcl(CannedAccessControlList.Private);
        try {
            oss.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes), metadata));
            return key;
        } catch (OSSException | ClientException exception) {
            // 云端可能已保存文件但响应丢失，按本次随机文件名尝试删除，失败时只记录固定错误。
            try {
                oss.deleteObject(bucket, key);
            } catch (OSSException | ClientException cleanupFailure) {
                System.getLogger(getClass().getName())
                        .log(System.Logger.Level.ERROR, "上传失败后的OSS对象清理失败，需要检查孤立对象");
            }
            throw unavailable();
        }
    }

    /**
     * 打开 OSS 私有文件，调用前先检查权限，使用后必须关闭流以释放连接。
     *
     * @param key 来自数据库的本站对象键，禁止 URL、路径穿越及其他前缀
     * @return 待关闭的对象流；不存在返回 404，云端失败返回 503
     */
    public InputStream open(String key) {
        requireKey(key);
        try {
            return oss.getObject(bucket, key).getObjectContent();
        } catch (OSSException exception) {
            if ("NoSuchKey".equals(exception.getErrorCode())) {
                throw new BusinessException(404, "NOT_FOUND", "文件不存在");
            }
            throw unavailable();
        } catch (ClientException exception) {
            throw unavailable();
        }
    }

    /** 检查 OSS 文件是否存在，不下载内容或访问外部链接。 */
    public boolean exists(String key) {
        requireKey(key);
        try {
            return oss.doesObjectExist(bucket, key);
        } catch (OSSException | ClientException exception) {
            throw unavailable();
        }
    }

    /** 删除 OSS 文件，业务层先确认无人引用，云端删除失败时报错。 */
    public void delete(String key) {
        requireKey(key);
        try {
            oss.deleteObject(bucket, key);
        } catch (OSSException | ClientException exception) {
            throw unavailable();
        }
    }

    /** 检查 OSS 文件路径，只接受完整合法的对象键，不通过解码或整理路径放宽检查。 */
    private void requireKey(String key) {
        if (key == null || key.length() > 512 || !key.startsWith(prefix)
                || !FileUtils.validObjectKey(key.substring(prefix.length()))) {
            throw new BusinessException(400, "VALIDATION_ERROR", "对象键不符合本站存储边界");
        }
    }

    /** 返回 OSS 服务错误，不带 SDK 异常、签名、Bucket 或内部地址。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "对象存储暂不可用");
    }
}
