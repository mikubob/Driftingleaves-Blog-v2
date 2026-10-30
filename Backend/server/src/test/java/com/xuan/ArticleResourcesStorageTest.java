package com.xuan;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.xuan.client.AliOssClient;
import com.xuan.content.ArticleResources;
import com.xuan.entity.BlogFile;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AliOssProperties;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.FileProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只替换 OSS SDK 的网络边界，实际调用现有客户端和附件存储逻辑，不访问真实云账号。
 */
class ArticleResourcesStorageTest {

    @TempDir
    Path directory;

    @Test
    void optionalOssUsesExistingClientPrivateRandomKeyAndClosesReadStream() throws Exception {
        OSS sdk = mock(OSS.class);
        AliOssProperties properties = ossProperties();
        FileProperties files = new FileProperties();
        files.setLocalRoot(directory);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("ossClient", new AliOssClient(sdk, properties));
        ArticleResources resources = new ArticleResources(new AuthProperties(), files, properties,
                beans.getBeanProvider(AliOssClient.class));
        Path temporary = resources.temporary();
        Files.write(temporary, new byte[]{1, 2, 3});
        BlogFile row = new BlogFile();
        row.setSizeBytes(BigInteger.valueOf(3));
        row.setMimeType("image/png");
        resources.store(temporary, "png", row);
        verify(sdk).putObject(any(PutObjectRequest.class));
        assertThat(row.getStorageType()).isEqualTo("oss");
        assertThat(row.getObjectKey()).matches("test-prefix/[A-Za-z0-9_-]{43}\\.png");
        OSSObject object = new OSSObject();
        boolean[] closed = {false};
        object.setObjectContent(new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override
            public void close() {
                closed[0] = true;
            }
        });
        when(sdk.getObject(properties.getBucket(), row.getObjectKey())).thenReturn(object);
        assertThat(resources.read(row)).containsExactly(1, 2, 3);
        assertThat(closed[0]).isTrue();
        resources.delete(row);
        verify(sdk).deleteObject(properties.getBucket(), row.getObjectKey());
        resources.cleanup(temporary, null);
        assertThat(temporary).doesNotExist();
    }

    @Test
    void localStorageChecksActualSizeAndRejectsUnsafeStoredPaths() throws Exception {
        FileProperties files = new FileProperties();
        files.setLocalRoot(directory.resolve("root"));
        ArticleResources resources = new ArticleResources(new AuthProperties(), files, new AliOssProperties(),
                new DefaultListableBeanFactory().getBeanProvider(AliOssClient.class));
        Path temporary = resources.temporary();
        Files.write(temporary, new byte[]{1, 2, 3});
        BlogFile row = new BlogFile();
        row.setSizeBytes(BigInteger.valueOf(3));
        row.setMimeType("image/png");
        resources.store(temporary, "png", row);
        resources.requireAvailable(row);
        assertThat(resources.read(row)).containsExactly(1, 2, 3);
        row.setSizeBytes(BigInteger.valueOf(4));
        assertThatThrownBy(() -> resources.read(row)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
        row.setSizeBytes(BigInteger.valueOf(3));
        resources.delete(row);
        resources.delete(row);
        row.setObjectKey("missing/folder/leaf.png");
        resources.delete(row);
        row.setObjectKey("../outside.png");
        assertThatThrownBy(() -> resources.delete(row)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
    }

    /** 配置只使用虚构边界值，SDK 全部为替身，不包含真实账号。 */
    private AliOssProperties ossProperties() {
        AliOssProperties properties = new AliOssProperties();
        properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.setRegion("cn-hangzhou");
        properties.setBucket("isolated-test-bucket");
        properties.setAccessKeyId("boundary-id");
        properties.setAccessKeySecret("boundary-secret");
        properties.setKeyPrefix("test-prefix/");
        return properties;
    }
}
