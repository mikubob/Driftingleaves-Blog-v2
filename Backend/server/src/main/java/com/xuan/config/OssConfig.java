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

/** OSS 只在开关启用时创建，关闭时不用凭据，也不访问云资源。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.oss", name = "enabled", havingValue = "true")
public class OssConfig {
    /** 启用后先查配置，再用 V4 签名创建共用 SDK；容器退出时关闭连接。 */
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

    /** 业务只能通过限定前缀的客户端读写文件，不能返回云端公开直链。 */
    @Bean
    public AliOssClient aliOssClient(OSS oss, AliOssProperties properties) {
        return new AliOssClient(oss, properties);
    }
}
