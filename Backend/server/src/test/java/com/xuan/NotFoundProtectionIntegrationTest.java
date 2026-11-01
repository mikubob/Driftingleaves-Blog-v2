package com.xuan;

import com.xuan.properties.AuthProperties;
import com.xuan.utils.TokenUtils;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 用真实隔离 MySQL/Redis 验证 HTTP、原子计数和封禁过期，不连接业务服务。 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class NotFoundProtectionIntegrationTest {

    private static final String ORIGIN = "https://blog.example.test";
    private static final String IP = "198.51.100.10";
    private static IsolatedServices services;

    @Autowired
    MockMvc mvc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AuthProperties properties;

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) throws Exception {
        services = new IsolatedServices();
        registry.add("app.oss.enabled", () -> false);
        registry.add("app.mail.enabled", () -> false);
        registry.add("spring.datasource.url", () -> services.jdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> services.redisPort);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.database", () -> 0);
        registry.add("app.auth.redis-prefix", () -> "dl:not-found-test:");
        registry.add("app.auth.allowed-origins", () -> ORIGIN);
        registry.add("app.auth.trusted-proxies", () -> "127.0.0.1");
        registry.add("app.auth.frontend-complete-url", () -> ORIGIN + "/oauth/complete");
    }

    @BeforeEach
    void reset() {
        try (var connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @AfterAll
    static void stop() {
        if (services != null) {
            services.close();
        }
    }

    @Test
    void backend404AndBothFrontendsShareOneBudgetAndBlockBeforeBusiness() throws Exception {
        for (int index = 0; index < 10; index++) {
            code(report(index % 2 == 0 ? "blog" : "admin"), 200, "OK");
            code(call(get("/blog/categories/missing-" + index)), 404, "NOT_FOUND");
        }
        code(call(get("/blog/categories/missing-last")), 403, "ACCESS_BLOCKED");
        assertThat(redis.opsForValue().get(key(":404"))).isEqualTo("21");
        assertThat(redis.getExpire(key(":blocked"))).isBetween(86395L, 86400L);
        for (String endpoint : List.of("/home/profile", "/cv/skills", "/blog/articles",
                "/admin/auth/captcha", "/blog/ws/online")) {
            MvcResult blocked = call(get(endpoint));
            code(blocked, 403, "ACCESS_BLOCKED");
            assertThat(Integer.parseInt(blocked.getResponse().getHeader("Retry-After"))).isPositive();
            assertThat(blocked.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_visitor", Long.class)).isZero();
    }

    @Test
    void blockedVisitsDoNotExtendBanAndExpiryRestoresAccess() throws Exception {
        redis.opsForValue().set(key(":blocked"), "1", Duration.ofSeconds(30));
        code(report("blog"), 403, "ACCESS_BLOCKED");
        assertThat(redis.getExpire(key(":blocked"))).isBetween(1L, 30L);
        redis.expire(key(":blocked"), Duration.ZERO);
        redis.opsForValue().set(key(":404"), "20", Duration.ofSeconds(60));
        redis.expire(key(":404"), Duration.ZERO);
        code(report("blog"), 200, "OK");
        assertThat(redis.opsForValue().get(key(":404"))).isEqualTo("1");
        assertThat(redis.getExpire(key(":404"))).isBetween(55L, 60L);
    }

    @Test
    void concurrentReportsAllowExactlyTwentyBeforeBan() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> tasks = IntStream.range(0, 40)
                    .mapToObj(index -> (Callable<Integer>) () -> report(index % 2 == 0 ? "blog" : "admin")
                            .getResponse().getStatus()).toList();
            var results = executor.invokeAll(tasks);
            int accepted = 0;
            for (var result : results) {
                int status = result.get();
                assertThat(status).isIn(200, 403);
                if (status == 200) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(20);
        }
        assertThat(redis.opsForValue().get(key(":404"))).isEqualTo("21");
    }

    @Test
    void sourceBodyAndQueryAreValidatedWithoutCounting() throws Exception {
        code(call(post("/blog/security/not-found")), 403, "FORBIDDEN");
        code(call(post("/blog/security/not-found").header("Origin", "https://untrusted.example.test")),
                403, "FORBIDDEN");
        code(call(post("/admin/security/not-found?ip=198.51.100.99").header("Origin", ORIGIN)),
                400, "VALIDATION_ERROR");
        code(call(post("/blog/security/not-found").header("Origin", ORIGIN)
                .contentType("application/json").content("{}")), 400, "VALIDATION_ERROR");
        assertThat(redis.hasKey(key(":404"))).isFalse();
    }

    @Test
    void forwardingHeadersCannotSelectAnotherIpUnlessProxyIsTrusted() throws Exception {
        redis.opsForValue().set(key(":blocked"), "1", Duration.ofMinutes(1));
        code(call(post("/blog/security/not-found").header("Origin", ORIGIN)
                .header("X-Forwarded-For", "203.0.113.99").header("X-Real-IP", "203.0.113.99")),
                403, "ACCESS_BLOCKED");
        MvcResult trusted = mvc.perform(post("/blog/security/not-found").header("Origin", ORIGIN)
                .header("X-Forwarded-For", IP).with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    request.setServletPath(request.getRequestURI());
                    return request;
                })).andReturn();
        code(trusted, 403, "ACCESS_BLOCKED");
        MvcResult independent = mvc.perform(post("/blog/security/not-found").header("Origin", ORIGIN)
                .with(request -> {
                    request.setRemoteAddr("203.0.113.99");
                    request.setServletPath(request.getRequestURI());
                    return request;
                })).andReturn();
        code(independent, 200, "OK");
    }

    @Test
    void optionsAndRateLimitsNeverCountAs404() throws Exception {
        for (int index = 0; index < 31; index++) {
            MvcResult result = call(get("/blog/auth/captcha"));
            assertThat(result.getResponse().getStatus()).isEqualTo(index < 30 ? 200 : 429);
        }
        assertThat(redis.hasKey(key(":404"))).isFalse();
        redis.opsForValue().set(key(":blocked"), "1", Duration.ofMinutes(1));
        assertThat(call(options("/blog/security/not-found").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void serviceErrorsDoNotCountAndRedisScriptFailureRejectsReport() throws Exception {
        for (int index = 0; index < 25; index++) {
            code(call(get("/error").with(request -> {
                request.setDispatcherType(DispatcherType.ERROR);
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
                return request;
            })), 500, "INTERNAL_ERROR");
        }
        assertThat(redis.hasKey(key(":404"))).isFalse();
        // 给真实 Redis 注入错误类型，确认 Lua 失败不会被当成上报成功。
        redis.opsForList().leftPush(key(":404"), "invalid-type");
        code(report("blog"), 503, "SERVICE_UNAVAILABLE");
        assertThat(redis.hasKey(key(":blocked"))).isFalse();
    }

    /** 所有测试请求都使用明确的虚构直连地址。 */
    private MvcResult call(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.with(request -> {
            request.setRemoteAddr(IP);
            request.setServletPath(request.getRequestURI());
            return request;
        })).andReturn();
    }

    /** 上报入口无需登录，来源必须在已配置白名单中。 */
    private MvcResult report(String site) throws Exception {
        return call(post("/" + site + "/security/not-found").header("Origin", ORIGIN));
    }

    /** 只检查对外协议，不复制生产计数算法。 */
    private void code(MvcResult result, int status, String code) {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).get("code").asString()).isEqualTo(code);
    }

    /** 测试只操作隔离前缀下的当前虚构 IP。 */
    private String key(String suffix) {
        return properties.getRedisPrefix() + "abuse:ip:" + TokenUtils.digest(IP) + suffix;
    }
}
