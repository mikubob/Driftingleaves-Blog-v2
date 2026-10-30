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

/** 条件装配共享 OSS SDK；关闭时不创建客户端、不要求凭据、不接触云资源。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.oss", name = "enabled", havingValue = "true")
public class OssConfig {
    /** 启用时校验外部配置并使用 V4 签名；复用单例，关闭容器时释放连接资源。 */
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

    /** 应用业务只能通过限定前缀的客户端访问文件，不返回云端公开地址。 */
    @Bean
    public AliOssClient aliOssClient(OSS oss, AliOssProperties properties) {
        return new AliOssClient(oss, properties);
    }
}
