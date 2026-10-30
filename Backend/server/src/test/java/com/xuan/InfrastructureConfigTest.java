package com.xuan;

import com.aliyun.oss.OSS;
import com.xuan.client.AliOssClient;
import com.xuan.config.OssConfig;
import com.xuan.config.VirtualThreadConfig;
import com.xuan.properties.AliOssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实际检查配置、组件加载和关闭，不向 OSS 发网络请求。 */
class InfrastructureConfigTest {
    @Test
    void virtualExecutorRunsRealVirtualThreadsAndClosesWithContainer() throws Exception {
        ExecutorService executor;
        try (var context = new AnnotationConfigApplicationContext(VirtualThreadConfig.class)) {
            executor = context.getBean("auditExecutor", ExecutorService.class);
            assertThat(executor.submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
        }
        assertThat(executor.isTerminated()).isTrue();
        assertThatThrownBy(() -> executor.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void ossIsDisabledWithoutCredentialsAndRejectsIncompleteEnabledConfiguration() {
        var runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class, OssConfig.class);
        runner.run(context -> assertThat(context).doesNotHaveBean(OSS.class).doesNotHaveBean(AliOssClient.class));
        runner.withPropertyValues("app.oss.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledConfigurationBuildsRealSdkAndClosesItWithoutCloudCalls() {
        var runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class, OssConfig.class)
                .withPropertyValues("app.oss.enabled=true", "app.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "app.oss.region=cn-hangzhou", "app.oss.bucket=isolated-test-bucket",
                        "app.oss.access-key-id=boundary-id", "app.oss.access-key-secret=boundary-secret");
        runner.run(context -> assertThat(context).hasSingleBean(OSS.class).hasSingleBean(AliOssClient.class));
    }

    /** 加载实际application配置验证默认启用，SDK使用虚构凭据装配，不请求云端。 */
    @Test
    void applicationDefaultsToOssAndRejectsMissingCredentials() throws Exception {
        var application = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).getFirst();
        var runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class, OssConfig.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(application))
                .withPropertyValues("OSS_ACCESS_KEY_ID=", "OSS_ACCESS_KEY_SECRET=");
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com", "OSS_REGION=cn-hangzhou",
                "OSS_BUCKET=isolated-test-bucket", "OSS_ACCESS_KEY_ID=boundary-id", "OSS_ACCESS_KEY_SECRET=boundary-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OSS.class).hasSingleBean(AliOssClient.class);
                    assertThat(context.getEnvironment().getProperty("app.oss.enabled", Boolean.class)).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AliOssProperties.class)
    static class PropertiesConfig { }
}
