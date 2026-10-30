package com.xuan;

import com.aliyun.oss.OSS;
import com.xuan.client.AliOssClient;
import com.xuan.config.OssConfig;
import com.xuan.config.VirtualThreadConfig;
import com.xuan.properties.AliOssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AliOssProperties.class)
    static class PropertiesConfig { }
}
