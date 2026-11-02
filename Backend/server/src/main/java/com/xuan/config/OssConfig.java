package com.xuan.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.xuan.client.AliOssClient;
import com.xuan.properties.AliOssProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 OSS 存储，只在启用时创建客户端，关闭时不读取凭据或访问云端。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.oss", name = "enabled", havingValue = "true")
public class OssConfig {

    /** 创建 OSS SDK，先检查配置，使用 V4 签名，应用关闭时释放连接。 */
    @Bean(destroyMethod = "shutdown")
    public OSS oss(AliOssProperties properties) {
        properties.validateEnabled();
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setSignatureVersion(SignVersion.V4);
        config.setConnectionTimeout(2_000);
        config.setSocketTimeout(10_000);
        config.setMaxErrorRetry(0);
        return OSSClientBuilder.create().endpoint(properties.getEndpoint()).region(properties.getRegion())
                .credentialsProvider(new DefaultCredentialProvider(properties.getAccessKeyId(),
                        properties.getAccessKeySecret(), properties.getSecurityToken()))
                .clientConfiguration(config).build();
    }

    /** 创建 OSS 文件客户端，只允许读写指定前缀下的文件，不返回云端直链。 */
    @Bean
    public AliOssClient aliOssClient(OSS oss, AliOssProperties properties) {
        return new AliOssClient(oss, properties);
    }
}
