package com.xuan.client;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AliOssProperties;
import com.xuan.utils.TokenUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** OSS 对象键由服务端生成，读写都只能在专属前缀里进行，不提供公开直链。 */
public class AliOssClient {
    private final OSS oss;
    private final String bucket;
    private final String prefix;

    /** SDK 由容器关闭，这里只留下用得着的配置，不保留明文凭据。 */
    public AliOssClient(OSS oss, AliOssProperties properties) {
        properties.validateEnabled();
        this.oss = oss;
        this.bucket = properties.getBucket();
        this.prefix = properties.getKeyPrefix();
    }

    /**
     * 上传已经过业务检查的文件；类型、归属和数据库失败后的补偿仍由文件业务处理。
     * @param bytes 非空文件内容，不超过当前 10MiB 文件大小约定
     * @param extension 已与内容识别一致的 png、jpg 或 jpeg，不使用原文件名
     * @return 专属前缀下的随机对象键，只有确认上传成功才返回
     */
    public String upload(byte[] bytes, String extension) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024
                || !("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension))) {
            throw new BusinessException(400, "VALIDATION_ERROR", "文件大小或扩展名不符合要求");
        }
        String key = prefix + TokenUtils.random() + "." + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("png".equals(extension) ? "image/png" : "image/jpeg");
        // 新对象一律设为私有，避免继承公开读取设置；不改 Bucket 或已有对象的 ACL。
        metadata.setObjectAcl(CannedAccessControlList.Private);
        try {
            oss.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes), metadata));
            return key;
        } catch (OSSException | ClientException exception) {
            // 云端可能已经写入但响应丢失，按本次随机键补偿，失败只留固定运维消息。
            try {
                oss.deleteObject(bucket, key);
            } catch (OSSException | ClientException cleanupFailure) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "上传失败后的OSS对象清理失败，需要检查孤立对象");
            }
            throw unavailable();
        }
    }

    /**
     * 权限检查通过后打开私有对象，用完必须关闭流，否则 SDK 连接不能释放。
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

    /** 发布引用前先确认私有对象还在，不下载文件，也不访问外部展示 URL。 */
    public boolean exists(String key) {
        requireKey(key);
        try {
            return oss.doesObjectExist(bucket, key);
        } catch (OSSException | ClientException exception) {
            throw unavailable();
        }
    }

    /** 业务确认文件没人引用后才能删，云端删除失败就报错，不能当作已经删掉。 */
    public void delete(String key) {
        requireKey(key);
        try {
            oss.deleteObject(bucket, key);
        } catch (OSSException | ClientException exception) {
            throw unavailable();
        }
    }

    /** 先检查完整对象键再调用 SDK，不靠解码或整理路径来接收含糊的输入。 */
    private void requireKey(String key) {
        if (key == null || key.length() > 512 || !key.startsWith(prefix)
                || !key.substring(prefix.length()).matches("[A-Za-z0-9_-]+\\.(?:png|jpg|jpeg)")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "对象键不符合本站存储边界");
        }
    }

    /** 对外只报固定错误，不把 SDK 异常、签名、Bucket 和内部地址带出去。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "对象存储暂不可用");
    }
}
