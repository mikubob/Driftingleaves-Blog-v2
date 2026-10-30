package com.xuan.client;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AliOssProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OSS 网络调用用替身，客户端逻辑和配置检查照常执行，不访问真实 Bucket。
 */
class AliOssClientTest {

    private final OSS sdk = mock(OSS.class);
    private final AliOssProperties properties = properties();
    private final AliOssClient client = new AliOssClient(sdk, properties);

    @Test
    void generatesPrivateRandomObjectWithoutExposingPublicUrlAndClosesReadStream() throws Exception {
        byte[] bytes = {1, 2, 3};
        String key = client.upload(bytes, "png");
        assertThat(key).matches("test-prefix/[A-Za-z0-9_-]{43}\\.png");
        ArgumentCaptor<PutObjectRequest> upload = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(sdk).putObject(upload.capture());
        assertThat(upload.getValue().getBucketName()).isEqualTo("isolated-test-bucket");
        assertThat(upload.getValue().getMetadata().getContentLength()).isEqualTo(3);
        assertThat(upload.getValue().getMetadata().getContentType()).isEqualTo("image/png");
        assertThat(upload.getValue().getMetadata().getRawMetadata()).containsEntry("x-oss-object-acl", "private");
        assertThat(upload.getValue().getInputStream().readAllBytes()).containsExactly(bytes);
        OSSObject object = new OSSObject();
        var stream = mock(ByteArrayInputStream.class);
        object.setObjectContent(stream);
        when(sdk.getObject(properties.getBucket(), key)).thenReturn(object);
        try (var opened = client.open(key)) {
            assertThat(opened).isSameAs(stream);
        }
        verify(stream).close();
        client.delete(key);
        verify(sdk).deleteObject(properties.getBucket(), key);
    }

    @Test
    void rejectsKeysOutsidePrefixAndInvalidUploadsBeforeCallingSdk() {
        for (String key : List.of("../a.png", "test-prefix/../a.png", "test-prefix/%2e%2e/a.png",
                "test-prefix/a\\b.png", "other/a.png", "https://example.test/a.png", "test-prefix/a.png?secret=x")) {
            assertThatThrownBy(() -> client.open(key)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> client.delete(key)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> client.exists(key)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> client.upload(new byte[0], "png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.upload(new byte[1], "../png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.upload(new byte[10 * 1024 * 1024 + 1], "png")).isInstanceOf(
                BusinessException.class);
        verifyNoInteractions(sdk);
    }

    @Test
    void reportsCloudFailuresWithoutCredentialOrSdkMessage() {
        when(sdk.putObject(any(PutObjectRequest.class))).thenThrow(new ClientException("secret-bearing-sdk-message"));
        assertThatThrownBy(() -> client.upload(new byte[]{1}, "jpg")).isInstanceOfSatisfying(BusinessException.class,
                error -> {
                    assertThat(error.getStatus()).isEqualTo(503);
                    assertThat(error.getMessage()).doesNotContain("secret");
                });
        String key = "test-prefix/object.png";
        when(sdk.doesObjectExist(properties.getBucket(), key)).thenReturn(true, false);
        assertThat(client.exists(key)).isTrue();
        assertThat(client.exists(key)).isFalse();
        when(sdk.doesObjectExist(properties.getBucket(), key)).thenThrow(
                new ClientException("secret-bearing-sdk-message"));
        assertThatThrownBy(() -> client.exists(key)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
        OSSException missing = mock(OSSException.class);
        when(missing.getErrorCode()).thenReturn("NoSuchKey");
        when(sdk.getObject(properties.getBucket(), key)).thenThrow(missing);
        assertThatThrownBy(() -> client.open(key)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(404));
        doThrow(new ClientException("secret-bearing-sdk-message")).when(sdk).deleteObject(properties.getBucket(), key);
        assertThatThrownBy(() -> client.delete(key)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
    }

    @Test
    void rejectsIncompleteOrUnsafeEnabledConfiguration() {
        properties.setEndpoint("http://example.test");
        assertThatThrownBy(properties::validateEnabled).isInstanceOf(IllegalArgumentException.class);
        properties.setEndpoint("https://example.test");
        properties.setKeyPrefix("../");
        assertThatThrownBy(properties::validateEnabled).isInstanceOf(IllegalArgumentException.class);
        properties.setKeyPrefix("test-prefix/");
        properties.setAccessKeySecret("");
        assertThatThrownBy(properties::validateEnabled).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedUploadCompensatesItsOwnRandomObjectKey() {
        doThrow(new ClientException("boundary network failure")).when(sdk).putObject(any(PutObjectRequest.class));
        assertThatThrownBy(() -> client.upload(new byte[]{1}, "png")).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
        ArgumentCaptor<PutObjectRequest> upload = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(sdk).putObject(upload.capture());
        verify(sdk).deleteObject(properties.getBucket(), upload.getValue().getKey());
    }

    private static AliOssProperties properties() {
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
