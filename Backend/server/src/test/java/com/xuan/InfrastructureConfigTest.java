package com.xuan;

import com.aliyun.oss.OSS;
import com.xuan.client.AliOssClient;
import com.xuan.config.AuthWebConfig;
import com.xuan.config.OssConfig;
import com.xuan.config.VirtualThreadConfig;
import com.xuan.properties.AliOssProperties;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.PublicSiteProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 实际检查配置、组件加载和关闭，不向 OSS 发网络请求。
 */
class InfrastructureConfigTest {

    @Test
    void virtualExecutorRunsRealVirtualThreadsAndClosesWithContainer() throws Exception {
        ExecutorService executor;
        try (var context = new AnnotationConfigApplicationContext(VirtualThreadConfig.class)) {
            executor = context.getBean("auditExecutor", ExecutorService.class);
            assertThat(executor.submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
        }
        assertThat(executor.isTerminated()).isTrue();
        assertThatThrownBy(() -> executor.execute(() -> {
        })).isInstanceOf(RejectedExecutionException.class);
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

    /** 三个环境都实际加载主配置与环境文件，缺少OSS凭据仍须拒绝启动。 */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prd"})
    void applicationDefaultsToOssAndRejectsMissingCredentials(String profile) {
        var runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class, OssConfig.class)
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile,
                        "OSS_ACCESS_KEY_ID=", "OSS_ACCESS_KEY_SECRET=");
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com", "OSS_REGION=cn-hangzhou",
                        "OSS_BUCKET=isolated-test-bucket", "OSS_ACCESS_KEY_ID=boundary-id",
                        "OSS_ACCESS_KEY_SECRET=boundary-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OSS.class).hasSingleBean(AliOssClient.class);
                    assertThat(context.getEnvironment().getProperty("app.oss.enabled", Boolean.class)).isTrue();
                });
    }

    /** 只加载真实配置，不装配数据库或SMTP连接，检查合并与环境变量引用。 */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prd"})
    void selectedProfileMergesCommonSettingsAndResolvesConnectionProperties(String profile) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile,
                        "DB_URL=jdbc:mysql://127.0.0.1:1/config_only", "DB_USERNAME=config-user",
                        "DB_PASSWORD=config-password", "REDIS_HOST=127.0.0.1",
                        "SMTP_HOST=smtp.qq.com", "SMTP_PORT=587", "SMTP_USERNAME=sender@example.test",
                        "SMTP_PASSWORD=smtp-test-password", "BLOG_BASE_URL=https://blog.example.test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getActiveProfiles()).containsExactly(profile);
                    assertThat(environment.getPropertySources().toString())
                            .contains("application-" + profile + ".yml");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:mysql://127.0.0.1:1/config_only");
                    assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("config-user");
                    assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("config-password");
                    assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
                    assertThat(environment.getProperty("spring.mail.host")).isEqualTo("smtp.qq.com");
                    assertThat(environment.getProperty("spring.mail.port", Integer.class)).isEqualTo(587);
                    assertThat(environment.getProperty("spring.mail.password")).isEqualTo("smtp-test-password");
                    assertThat(environment.getProperty("app.mail.from")).isEqualTo("sender@example.test");
                    assertThat(environment.getProperty("app.mail.enabled", Boolean.class)).isTrue();
                    assertThat(environment.getProperty("app.mail.confirm-url"))
                            .isEqualTo("https://blog.example.test/subscription/confirm");
                    assertThat(environment.getProperty("app.mail.unsubscribe-url"))
                            .isEqualTo("https://blog.example.test/subscription/unsubscribe");
                    assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable",
                            Boolean.class)).isTrue();
                    assertThat(environment.getProperty("spring.mail.properties.mail.smtp.timeout", Integer.class))
                            .isEqualTo(3000);
                    assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
                    assertThat(environment.getProperty("sa-token.is-read-cookie", Boolean.class)).isFalse();
                    String redisPrefix = "dl:v2:";
                    if (!"prd".equals(profile)) {
                        redisPrefix += profile + ":";
                    }
                    assertThat(environment.getProperty("app.auth.redis-prefix")).isEqualTo(redisPrefix);
                });
    }

    /** 默认开发环境和显式选择环境共用Spring Boot配置加载流程。 */
    @Test
    void applicationSelectsDevByDefault() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("dev"));
    }

    /** 使用真实环境配置与CORS过滤器，覆盖代理保留Origin的登录、写入及预检。 */
    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:5173", "http://localhost:5173",
            "http://127.0.0.1:5174", "http://localhost:5174",
            "http://127.0.0.1:5175", "http://localhost:5175",
            "http://127.0.0.1:5176", "http://localhost:5176"})
    void devCorsAllowsConfiguredFrontendOrigins(String origin) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    var auth = Binder.get(context.getEnvironment()).bind("app.auth", AuthProperties.class).get();
                    var filter = new AuthWebConfig(null, null, null, auth, null)
                            .corsFilter(JsonMapper.builder().build()).getFilter();
                    var preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/blog/me/profile");
                    preflight.setServerPort(5922);
                    preflight.addHeader("Origin", origin);
                    preflight.addHeader("Access-Control-Request-Method", "PATCH");
                    preflight.addHeader("Access-Control-Request-Headers",
                            "Authorization,Content-Type,If-Revision,X-Captcha-Id,X-Captcha-Code");
                    var preflightResponse = new MockHttpServletResponse();
                    filter.doFilter(preflight, preflightResponse, new MockFilterChain());
                    assertThat(preflightResponse.getStatus()).isEqualTo(200);
                    assertThat(preflightResponse.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
                    assertThat(preflightResponse.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
                    assertThat(preflightResponse.getHeader("Access-Control-Allow-Headers"))
                            .contains("X-Captcha-Id", "X-Captcha-Code");

                    var login = new MockHttpServletRequest("POST", "/api/v1/blog/auth/login");
                    login.setServerPort(5922);
                    login.addHeader("Origin", origin);
                    login.setContentType("application/json");
                    var response = new MockHttpServletResponse();
                    var chain = new MockFilterChain();
                    filter.doFilter(login, response, chain);
                    assertThat(chain.getRequest()).isSameAs(login);
                    assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
                    assertThat(response.getHeader("Access-Control-Expose-Headers"))
                            .contains("X-Revision", "Location", "Retry-After");
                });
    }

    /** 本地来源仅在dev默认开放，显式白名单覆盖默认值，拒绝来源时不能进入业务。 */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prd"})
    void corsRejectsUnknownOriginsAndHonorsEnvironmentOverride(String profile) {
        var runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile);
        runner.run(context -> {
            var auth = Binder.get(context.getEnvironment()).bind("app.auth", AuthProperties.class).get();
            assertThat(auth.getAllowedOrigins().contains("http://127.0.0.1:5174")).isEqualTo("dev".equals(profile));
            assertThat(auth.getAllowedOrigins().contains("http://localhost:5174")).isEqualTo("dev".equals(profile));
            assertThat(auth.getAllowedOrigins().contains("http://localhost:5176")).isEqualTo("dev".equals(profile));
            var filter = new AuthWebConfig(null, null, null, auth, null)
                    .corsFilter(JsonMapper.builder().build()).getFilter();
            var request = new MockHttpServletRequest("POST", "/api/v1/blog/auth/login");
            request.setServerPort(5922);
            request.addHeader("Origin", "http://localhost:5177");
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
            assertThat(response.getContentAsString()).contains("FORBIDDEN");
            assertThat(chain.getRequest()).isNull();
        });
        runner.withPropertyValues("FRONTEND_ORIGINS=https://admin.example.test").run(context -> {
            var auth = Binder.get(context.getEnvironment()).bind("app.auth", AuthProperties.class).get();
            assertThat(auth.getAllowedOrigins()).containsExactly("https://admin.example.test");
        });
    }

    /** 开发主页采集通过站点来源检查，仍拒绝用主页来源冒充简历站。 */
    @Test
    void devPublicSitesMatchDefaultFrontendAddresses() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    var sites = Binder.get(context.getEnvironment()).bind("app.sites", PublicSiteProperties.class).get();
                    sites.requireSite("home", "http://127.0.0.1:5173", "/");
                    sites.requireSite("cv", "http://127.0.0.1:5175", "/");
                    assertThatThrownBy(() -> sites.requireSite("cv", "http://127.0.0.1:5173", "/"))
                            .hasMessage("采集站点与来源路径不匹配");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AliOssProperties.class)
    static class PropertiesConfig {

    }
}
