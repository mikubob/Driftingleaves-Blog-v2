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

/** 有状态 OSS 客户端边界；对象键由服务端生成，所有访问限定在专属前缀，不生成公开直链。 */
public class AliOssClient {
    private final OSS oss;
    private final String bucket;
    private final String prefix;

    /** SDK 生命周期归容器，客户端仅保留所需配置快照，不保留明文凭据。 */
    public AliOssClient(OSS oss, AliOssProperties properties) {
        properties.validateEnabled();
        this.oss = oss;
        this.bucket = properties.getBucket();
        this.prefix = properties.getKeyPrefix();
    }

    /**
     * 上传业务已验证内容的文件；类型识别、资源归属和数据库补偿由文件业务负责。
     * @param bytes 非空文件内容，不超过当前 10MiB 文件契约
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
        // 新对象明确私有；不修改 Bucket 或已有对象的 ACL，不继承意外的公开读取配置。
        metadata.setObjectAcl(CannedAccessControlList.Private);
        try {
            oss.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes), metadata));
            return key;
        } catch (OSSException | ClientException exception) {
            throw unavailable();
        }
    }

    /**
     * 打开业务鉴权后的私有对象，调用方必须关闭流以释放 SDK 连接。
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

    /** 删除已由业务确认无引用的对象；云端失败向上抛出，不伪造删除成功。 */
    public void delete(String key) {
        requireKey(key);
        try {
            oss.deleteObject(bucket, key);
        } catch (OSSException | ClientException exception) {
            throw unavailable();
        }
    }

    /** 在进入 SDK 前校验完整对象键，不做 URL 解码或路径归一化来接受歧义输入。 */
    private void requireKey(String key) {
        if (key == null || key.length() > 512 || !key.startsWith(prefix)
                || !key.substring(prefix.length()).matches("[A-Za-z0-9_-]+\\.(?:png|jpg|jpeg)")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "对象键不符合本站存储边界");
        }
    }

    /** 不将 SDK 异常、签名、Bucket 或内部地址放入公开错误。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "对象存储暂不可用");
    }
}
