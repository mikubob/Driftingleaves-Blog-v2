package com.xuan;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.annotation.RateLimit;
import com.xuan.audit.FailureAuditWriter;
import com.xuan.client.JustAuthClient;
import com.xuan.client.OAuthProviderClient;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.entity.BlogArticle;
import com.xuan.entity.BlogFile;
import com.xuan.entity.SysOperationLog;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.OperationLogMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.RedisRateLimiter;
import com.xuan.result.Result;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IMenuService;
import com.xuan.service.IPermissionService;
import com.xuan.service.IRoleService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.RedisOAuthStore;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.SessionVO;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.Cookie;
import me.zhyd.oauth.config.AuthDefaultSource;
import me.zhyd.oauth.utils.AuthChecker;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 身份接口走真实 MySQL、Redis、Service、Mapper 和 MVC，只有平台网络和指定失败使用替身。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IdentityIntegrationTest.Boundaries.class)
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class IdentityIntegrationTest {

    private static IsolatedServices services;
    private static final String PASSWORD = "Example-password-2026";
    private static final String ORIGIN = "https://blog.example.test";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @MockitoSpyBean
    IUserService users;

    @MockitoSpyBean
    IRoleService roles;

    @MockitoSpyBean
    IPermissionService permissions;

    @MockitoSpyBean
    IMenuService menus;

    @Autowired
    AuthorizationProvider authorization;

    @Autowired
    RedisOAuthStore store;

    @Autowired
    AuthProperties properties;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    SqlSessionFactory sqlSessionFactory;

    @MockitoSpyBean
    SessionManager sessions;

    @MockitoSpyBean
    OperationLogMapper auditLogs;

    @MockitoSpyBean
    FailureAuditWriter failureAudits;

    @Autowired
    RedisRateLimiter rateLimiter;
    private final List<CompletableFuture<Void>> pendingAudits = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) throws Exception {
        services = new IsolatedServices();
        // 隔离业务测试使用临时本地文件，不加载部署OSS或访问真实Bucket。
        registry.add("app.oss.enabled", () -> false);
        registry.add("spring.datasource.url", () -> services.jdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> services.redisPort);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.database", () -> 0);
        registry.add("app.auth.redis-prefix", () -> "dl:test:");
        registry.add("app.auth.bcrypt-strength", () -> 4);
        registry.add("app.auth.allowed-origins", () -> ORIGIN);
        registry.add("app.auth.frontend-complete-url", () -> ORIGIN + "/oauth/complete");
        registry.add("app.auth.rate.register", () -> 100);
        registry.add("app.auth.rate.oauth", () -> 100);
        for (String source : List.of("GITHUB", "GITEE")) {
            registry.add("app.auth.providers." + source + ".enabled", () -> true);
            registry.add("app.auth.providers." + source + ".client-id", () -> "isolated-test-app");
            registry.add("app.auth.providers." + source + ".client-secret", () -> "isolated-boundary-value");
            registry.add("app.auth.providers." + source + ".redirect-uri",
                    () -> ORIGIN + "/api/v1/auth/oauth/" + source + "/callback");
        }
    }

    @AfterAll
    static void stopIsolatedProcesses(@Autowired ConfigurableApplicationContext context) {
        context.getBean(LettuceConnectionFactory.class).destroy();
        context.getBean(HikariDataSource.class).close();
        if (services != null) {
            services.close();
        }
    }

    @BeforeEach
    void resetIsolatedData() {
        reset(sessions, users, roles, permissions, menus, auditLogs, failureAudits);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<Void> result = (CompletableFuture<Void>) invocation.callRealMethod();
            pendingAudits.add(result);
            return result;
        }).when(failureAudits).write(any(SysOperationLog.class));
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM blog_article");
        jdbc.update("DELETE FROM blog_file");
        jdbc.update("DELETE FROM sys_user");
        // 自建菜单从叶子开始清理，先释放权限的 RESTRICT 引用，不改初始化菜单和原有授权。
        while (jdbc.update(
                "DELETE m FROM sys_menu m LEFT JOIN sys_menu c ON c.parent_id=m.menu_id WHERE m.menu_code LIKE " +
                 "'test:%' AND c.menu_id IS NULL") > 0) {
        }
        jdbc.update("DELETE FROM sys_permission WHERE permission_code LIKE 'test:%'");
        jdbc.update("UPDATE sys_role SET status=1");
        jdbc.update(
                "UPDATE sys_role SET data_scope=CASE WHEN role_id=1 THEN 'ALL' ELSE 'SELF' END WHERE role_id IN (1,2," +
                 "3)");
        jdbc.update("UPDATE sys_permission SET status=1");
        jdbc.update("DELETE FROM sys_role WHERE role_id > 3");
        jdbc.update("UPDATE sys_menu SET status=1");
        redis.delete(redis.keys("dl:test:*"));
    }

    @Test
    void annotationRateLimitUsesUserTokenAndTrustedIpWithoutDoubleCountingAuth() throws Exception {
        String token = adminLogin("rate_admin");
        String another = login("rate_admin");
        for (int index = 1; index <= 2; index++) {
            final int address = index;
            assertCode(mvc.perform(req("GET", "/admin/test/rate-user")
                    .header("Authorization", "Bearer " + (index == 1 ? token : another))
                    .with(request -> {
                        request.setRemoteAddr("192.0.2." + address);
                        return request;
                    })).andReturn(), 200, "OK");
        }
        MvcResult limited = mvc.perform(req("GET", "/admin/test/rate-user").header("Authorization", "Bearer " + token)
                .with(request -> {
                    request.setRemoteAddr("192.0.2.3");
                    return request;
                })).andReturn();
        assertCode(limited, 429, "RATE_LIMITED");
        assertThat(Integer.parseInt(limited.getResponse().getHeader("Retry-After"))).isPositive();
        for (int index = 1; index <= 3; index++) {
            final int address = index;
            MvcResult result = mvc.perform(
                    req("GET", "/admin/test/rate-token").header("Authorization", "Bearer " + token)
                            .with(request -> {
                                request.setRemoteAddr("198.51.100." + address);
                                return request;
                            })).andReturn();
            assertCode(result, index <= 2 ? 200 : 429, index <= 2 ? "OK" : "RATE_LIMITED");
        }
        assertCode(mvc.perform(req("GET", "/admin/test/rate-token").header("Authorization", "Bearer " + another)
                .with(request -> {
                    request.setRemoteAddr("198.51.100.4");
                    return request;
                })).andReturn(), 200, "OK");
        assertThat(redis.keys("dl:test:rate:interface:*")).allMatch(
                key -> !key.contains(token) && !key.contains(another));
        assertThat(redis.keys("dl:test:rate:register:*")).hasSize(2);
        for (String key : redis.keys("dl:test:rate:register:*")) {
            assertThat(redis.opsForValue().get(key)).isEqualTo("1");
        }
    }

    @Test
    void passwordEndpointsShareTheirRealAnnotatedWindowAndRedisLimitsAreAtomic() throws Exception {
        register("rate_leaf");
        String token = login("rate_leaf");
        for (int index = 0; index < 10; index++) {
            assertCode(call(index % 2 == 0 ? "PUT" : "DELETE", "/me/password", token,
                    index % 2 == 0 ? "{\"currentPassword\":\"incorrect-password\",\"newPassword\":\"another-password\"}"
                            : "{\"currentPassword\":\"incorrect-password\"}"), 401, "AUTH_FAILED");
        }
        assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"incorrect-password\"}"), 429,
                "RATE_LIMITED");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Boolean>> requests = IntStream.range(0, 12).mapToObj(index -> (Callable<Boolean>) () -> {
                try {
                    rateLimiter.hit(List.of("dl:test:rate:atomic:ip", "dl:test:rate:atomic:user"), 60, 3);
                    return true;
                } catch (BusinessException exception) {
                    assertThat(exception.getCode()).isEqualTo("RATE_LIMITED");
                    return false;
                }
            }).toList();
            long allowed = 0;
            for (var future : executor.invokeAll(requests)) {
                if (future.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(3);
        }
    }

    @Test
    void successfulAuditSharesBusinessTransactionAndOuterRollbackCreatesOnlyFailure() throws Exception {
        String token = adminLogin("audit_admin");
        AtomicBoolean visibleInTransaction = new AtomicBoolean();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                String role = createRole(token, "audit_rollback");
                visibleInTransaction.set(
                        jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE target_id=? AND result=1",
                                Integer.class, role) == 1);
                status.setRollbackOnly();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        awaitAudits();
        assertThat(visibleInTransaction).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE role_code='audit_rollback'",
                Integer.class)).isZero();
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE result=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT error_message FROM sys_operation_log", String.class)).isEqualTo(
                "TRANSACTION_NOT_COMMITTED");
    }

    @Test
    void auditInsertFailureRollsBackBusinessThenWritesRedactedFailureOnVirtualThread() throws Exception {
        String token = adminLogin("audit_admin");
        AtomicBoolean independentVirtualTransaction = new AtomicBoolean();
        doAnswer(invocation -> {
            SysOperationLog log = invocation.getArgument(0);
            if (log.getResult() == 1) {
                throw new DataIntegrityViolationException("password=secret Bearer raw-token SQL-internal");
            }
            independentVirtualTransaction.set(Thread.currentThread().isVirtual()
                    && TransactionSynchronizationManager.isActualTransactionActive()
                    && RequestContextHolder.getRequestAttributes() == null);
            return new SqlSessionTemplate(sqlSessionFactory).getMapper(OperationLogMapper.class).insertLog(log);
        }).when(auditLogs).insertLog(any(SysOperationLog.class));
        assertCode(call("POST", "/admin/roles", token, roleCreateBody("audit_fail")), 503, "SERVICE_UNAVAILABLE");
        awaitAudits();
        assertThat(independentVirtualTransaction).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE role_code='audit_fail'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT result,error_message,operation_data FROM sys_operation_log"))
                .containsEntry("result", 0).containsEntry("error_message", "SERVICE_UNAVAILABLE")
                .containsEntry("operation_data", null);
    }

    @Test
    void auditRecordsActorTargetPermissionAndTrustedIpWithoutRequestSecrets() throws Exception {
        String token = adminLogin("audit_admin");
        Long actor = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username='audit_admin'", Long.class);
        properties.setTrustedProxies(List.of("192.0.2.1", "192.0.2.2"));
        try {
            MvcResult result = mvc.perform(req("POST", "/admin/users").header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", "1.1.1.1, 198.51.100.9, 192.0.2.2")
                    .with(request -> {
                        request.setRemoteAddr("192.0.2.1");
                        return request;
                    })
                    .contentType("application/json").content(createUserBody("audit_created"))).andReturn();
            assertCode(result, 201, "OK");
            Map<String, Object> log = jdbc.queryForMap("SELECT * FROM sys_operation_log");
            assertThat(log).containsEntry("user_id", actor).containsEntry("operation_type", "create")
                    .containsEntry("operation_target", "sys:user").containsEntry("permission_code", "sys:user:create")
                    .containsEntry("target_id", Long.parseLong(data(result).get("userId").asString()))
                    .containsEntry("ip_address", "198.51.100.9").containsEntry("result", 1)
                    .containsEntry("operation_data", null).containsEntry("error_message", null);
            assertThat(log.toString()).doesNotContain(PASSWORD, token, "audit_created", "Authorization", "Bearer");
        } finally {
            properties.setTrustedProxies(List.of());
        }
    }

    /** 等失败日志真正写完再清理隔离数据，避免后台写入串到下一条测试。 */
    @AfterEach
    void awaitAudits() throws Exception {
        for (CompletableFuture<Void> pending : pendingAudits) {
            pending.get(10, TimeUnit.SECONDS);
        }
        pendingAudits.clear();
    }

    @Test
    void registrationLoginSessionsWhitelistAndValidation() throws Exception {
        MvcResult providerList = call("GET", "/auth/oauth/providers", null, null);
        assertCode(providerList, 200, "OK");
        assertThat(data(providerList).size()).isEqualTo(2);
        assertThat(data(providerList).get(0).get("source").asString()).isEqualTo("GITEE");
        assertThat(data(providerList).get(1).get("displayName").asString()).isEqualTo("GitHub");
        assertThat(data(providerList).get(0).properties()).hasSize(2);
        assertCode(call("POST", "/auth/oauth/github/authorize", null, "{\"operation\":\"LOGIN\"}"), 404, "NOT_FOUND");
        assertCode(call("GET", "/auth/oauth/UNKNOWN/callback", null, null), 404, "NOT_FOUND");
        MvcResult created = register("leaf");
        assertCode(created, 201, "OK");
        JsonNode me = data(created);
        assertThat(me.get("userId").isString()).isTrue();
        assertThat(me.get("lastLoginTime").isNull()).isTrue();
        assertThat(me.get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(created.getResponse().getContentAsString()).doesNotContain("passwordHash", "uuid");
        long id = Long.parseLong(me.get("userId").asString());
        assertThat(jdbc.queryForObject("SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, id)).isEqualTo(
                3);
        assertThat(
                jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).startsWith(
                "$2a$");
        assertCode(register("LEAF"), 409, "UNIQUE_CONFLICT");
        String token = login("leaf");
        String another = login("leaf");
        assertThat(another).isNotEqualTo(token);
        assertThat(StpUtil.getStpLogic().getTokenTimeout(token)).isBetween(7190L, 7200L);
        assertThat(redis.keys("dl:test:session:*")).isNotEmpty();
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("permissionCodes").size()).isEqualTo(8);
        assertThat(authorization.capabilities(id).permissionCodes()).containsExactlyInAnyOrder(
                "blog:comment:create", "blog:comment:deleteOwn", "blog:article:like",
                "blog:message:create", "blog:message:updateOwn", "blog:message:deleteOwn",
                "blog:rss:subscribe", "blog:rss:unsubscribe");
        assertCode(call("GET", "/me", null, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/blog/anything", null, null), 401, "UNAUTHENTICATED");
        assertCode(mvc.perform(req("GET", "/me").param("Authorization", "Bearer " + token)).andReturn(), 401,
                "UNAUTHENTICATED");
        assertCode(mvc.perform(req("GET", "/me").cookie(new Cookie("Authorization", token))).andReturn(), 401,
                "UNAUTHENTICATED");
        assertCode(call("POST", "/auth/register", null, "{\"username\":12}"), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/register", null, "{\"username\":\"a\",\"username\":\"b\"}"), 400,
                "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/register", null,
                        "{\"username\":\"leaf\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"叶\",\"status\":1}"),
                         400,
                "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/register", null, " ".repeat(8 * 1024 * 1024 + 1)), 413, "PAYLOAD_TOO_LARGE");
        assertCode(call("POST", "/auth/logout", token, null), 200, "OK");
        assertCode(call("POST", "/auth/logout", token, null), 200, "OK");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", id);
        assertCode(call("GET", "/me", another, null), 403, "ACCOUNT_DISABLED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", "wrong-password")), 401, "AUTH_FAILED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", PASSWORD)), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void profileRevisionAndCors() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        String token = login("leaf");
        String original = call("GET", "/me", token, null).getResponse().getHeader("X-Revision");
        assertCode(call("PATCH", "/me/profile", token, "{\"intro\":\"hello\"}"), 428, "PRECONDITION_REQUIRED");
        assertCode(patch(token, "*", "{\"intro\":\"hello\"}"), 400, "VALIDATION_ERROR");
        for (String body : List.of("{}", "{\"nickname\":null}", "{\"nickname\":\" \"}",
                "{\"userId\":\"1\"}", "{\"nickname\":12}", "{\"intro\":\"a\",\"intro\":\"b\"}")) {
            assertCode(patch(token, original, body), 400, "VALIDATION_ERROR");
        }
        // 头像不能指向本站附件 API，绝对 URL、编码或整理后的等价路径也不行。
        for (String avatar : List.of("javascript:alert(1)", "https://user:password@example.test/a.png",
                ORIGIN + "/api/v1/admin/files/1/content", ORIGIN + "/api/v1/blog/articles/1/files/2/content",
                ORIGIN + "/api/v1/admin/articles/1/files/2/content",
                ORIGIN + "/api/v1/%61dmin/files/1/content",
                ORIGIN + "/api/v1/temp/%2e%2e/admin/files/1/content")) {
            assertCode(patch(token, original, json.writeValueAsString(Map.of("avatar", avatar))), 400,
                    "VALIDATION_ERROR");
        }
        MvcResult patched = patch(token, original, "{\"intro\":\" hello \"}");
        assertCode(patched, 200, "OK");
        assertThat(data(patched).get("nickname").asString()).isEqualTo("叶");
        assertThat(data(patched).get("intro").asString()).isEqualTo("hello");
        String revised = patched.getResponse().getHeader("X-Revision");
        assertThat(revised).isNotEqualTo(original);
        assertCode(patch(token, original, "{\"intro\":null}"), 412, "REVISION_CONFLICT");
        assertCode(patch(token, revised, "{\"intro\":null}"), 200, "OK");
        String current = call("GET", "/me", token, null).getResponse().getHeader("X-Revision");
        MvcResult filled = patch(token, current,
                "{\"nickname\":\" 新昵称 \",\"email\":\" leaf@example.test \",\"avatar\":\" https://images.example" +
                 ".test/a.png \",\"intro\":\" 简介 \"}");
        assertCode(filled, 200, "OK");
        assertThat(data(filled).get("nickname").asString()).isEqualTo("新昵称");
        assertThat(data(filled).get("email").asString()).isEqualTo("leaf@example.test");
        assertThat(data(filled).get("avatar").asString()).isEqualTo("https://images.example.test/a.png");
        long other = Long.parseLong(data(register("other")).get("userId").asString());
        jdbc.update("UPDATE sys_user SET email='used@example.test' WHERE user_id=?", other);
        String filledRevision = filled.getResponse().getHeader("X-Revision");
        assertCode(patch(token, filledRevision, "{\"nickname\":\"不应保存\",\"email\":\"USED@example.test\"}"), 409,
                "UNIQUE_CONFLICT");
        assertThat(call("GET", "/me", token, null).getResponse().getHeader("X-Revision")).isEqualTo(filledRevision);
        MvcResult cleared = patch(token, filledRevision, "{\"email\":\" \",\"avatar\":null,\"intro\":\" \"}");
        assertCode(cleared, 200, "OK");
        assertThat(data(cleared).get("nickname").asString()).isEqualTo("新昵称");
        for (String field : List.of("email", "avatar", "intro")) {
            assertThat(data(cleared).get(field).isNull()).isTrue();
        }
        login("leaf");
        assertThat(call("GET", "/me", token, null).getResponse().getHeader("X-Revision"))
                .isEqualTo(cleared.getResponse().getHeader("X-Revision"));
        String beforeRole = call("GET", "/me", token, null).getResponse().getHeader("X-Revision");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,2)", id);
        assertThat(call("GET", "/me", token, null).getResponse().getHeader("X-Revision")).isNotEqualTo(beforeRole);
        assertCode(call("PATCH", "/me/profile", token, "{\"nicknamePresent\":true}"), 400, "VALIDATION_ERROR");
        var preflight = mvc.perform(req("OPTIONS", "/me").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "Authorization,If-Revision")).andReturn();
        assertThat(preflight.getResponse().getStatus()).isEqualTo(200);
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
        assertThat(mvc.perform(req("OPTIONS", "/me").header("Origin", "https://evil.test")
                .header("Access-Control-Request-Method", "GET")).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void scopesMenusAndBackendDoublePermission() throws Exception {
        long id = Long.parseLong(data(register("author")).get("userId").asString());
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,2)", id);
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES (9,'unrelated','其他','ALL')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,9)", id);
        assertThat(authorization.requireScope(id, "blog:article:update")).isEqualTo("SELF");
        String token = login("author");
        assertCode(call("GET", "/admin/test/query", token, null), 200, "OK");
        assertCode(call("GET", "/admin/test/users", token, null), 403, "FORBIDDEN");
        assertCode(call("GET", "/admin/test/no-permission", token, null), 403, "FORBIDDEN");
        assertThat(call("GET", "/me/menus", token, null).getResponse().getContentAsString())
                .contains("content/article/index", "\"menuType\":\"B\"", "\"isVisible\":0")
                .doesNotContain("system/user/index");
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=100");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(authorization.requireScope(id, "blog:article:update")).isEqualTo("SELF");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=2");
        assertCode(call("GET", "/admin/test/query", token, null), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,1)", id);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='sys:user:query'");
        assertCode(call("GET", "/admin/test/users", token, null), 403, "FORBIDDEN");
    }

    @Test
    void passwordNullRecentAuthLastMethodAndRevocation() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        String old = login("leaf");
        String recent = login("leaf");
        StpUtil.getStpLogic().getTokenSessionByToken(old).set("authenticatedAt", System.currentTimeMillis() - 301_000);
        String setBody = "{\"newPassword\":\"new-password-2026\",\"currentPassword\":\"" + PASSWORD + "\"}";
        String clearBody = "{\"currentPassword\":\"" + PASSWORD + "\"}";
        assertCode(call("PUT", "/me/password", old, setBody), 403, "REAUTH_REQUIRED");
        assertCode(call("DELETE", "/me/password", recent, clearBody), 409, "LAST_LOGIN_METHOD");
        jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,'UNKNOWN','not-available')", id);
        assertCode(call("DELETE", "/me/password", recent, clearBody), 409, "LAST_LOGIN_METHOD");
        jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,'GITHUB','verified')", id);
        assertCode(call("DELETE", "/me/password", recent, clearBody), 200, "OK");
        assertThat(
                jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isNull();
        assertCode(call("GET", "/me", old, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", recent, null), 401, "UNAUTHENTICATED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", PASSWORD)), 401, "AUTH_FAILED");
        OAuthFlow flow = start("LOGIN", null, null);
        String oauthToken = data(exchange(callbackTicket(flow, "verified"), flow.browser(), null)).get("session")
                .get("accessToken").asString();
        assertCode(call("DELETE", "/me/password", oauthToken, clearBody), 409, "STATE_CONFLICT");
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", oauthToken, null), 409, "LAST_LOGIN_METHOD");
        StpUtil.getStpLogic().getTokenSessionByToken(oauthToken).set("method", "PASSWORD");
        assertCode(call("PUT", "/me/password", oauthToken, "{\"newPassword\":\"new-password-2026\"}"), 403,
                "REAUTH_REQUIRED");
        StpUtil.getStpLogic().getTokenSessionByToken(oauthToken).set("method", "OAUTH");
        assertCode(call("PUT", "/me/password", oauthToken,
                "{\"newPassword\":\"new-password-2026\",\"currentPassword\":null}"), 400, "VALIDATION_ERROR");
        assertCode(call("PUT", "/me/password", oauthToken, "{\"newPassword\":\"new-password-2026\"}"), 200, "OK");
        assertCode(call("GET", "/me", oauthToken, null), 401, "UNAUTHENTICATED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", "new-password-2026")), 200, "OK");
    }

    @Test
    void redisFailureRollsBackPasswordAndCommitFailureCleansNewToken() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        String token = login("leaf");
        String before = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id);
        doThrow(new RedisConnectionFailureException("injected external failure")).when(sessions).revokeAll(id);
        assertCode(call("PUT", "/me/password", token,
                        "{\"newPassword\":\"new-password-2026\",\"currentPassword\":\"" + PASSWORD + "\"}"), 503,
                "SERVICE_UNAVAILABLE");
        assertThat(
                jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isEqualTo(
                before);
        reset(sessions);
        SessionVO[] created = new SessionVO[1];
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            created[0] = users.login(new LoginDTO("leaf", PASSWORD));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("injected commit failure");
                }
            });
            return null;
        })).isInstanceOf(IllegalStateException.class);
        assertThat(StpUtil.getStpLogic().getLoginIdByToken(created[0].accessToken())).isNull();
        assertThat(StpUtil.getStpLogic().getLoginIdByToken(token)).isNotNull();
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=3");
        assertCode(register("rollback"), 503, "SERVICE_UNAVAILABLE");
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='rollback'", Integer.class)).isZero();
    }

    @Test
    void oauthBrowserReplayExpiryAndVerifiedIdentity() throws Exception {
        OAuthFlow flow = start("LOGIN", null, null);
        assertCode(mvc.perform(req("GET", "/auth/oauth/GITHUB/callback").param("state", flow.state())
                        .param("code", "same").cookie(new Cookie("__Host-dl-oauth-browser", TokenUtils.random()))).andReturn(),
                400, "OAUTH_STATE_INVALID");
        String ticket = callbackTicket(flow, " SameIdentity ");
        assertCode(callback(flow, "same"), 400, "OAUTH_STATE_INVALID");
        assertCode(exchange(ticket, TokenUtils.random(), null), 400, "OAUTH_STATE_INVALID");
        MvcResult result = exchange(ticket, flow.browser(), null);
        assertCode(result, 200, "OK");
        assertThat(data(result).has("binding")).isFalse();
        assertThat(data(result).get("session").get("user").get("hasPassword").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT uuid FROM sys_user_oauth", String.class)).isEqualTo(" SameIdentity ");
        assertCode(exchange(ticket, flow.browser(), null), 400, "OAUTH_STATE_INVALID");
        OAuthFlow expiring = start("LOGIN", null, flow.browser());
        redis.expire("dl:test:oauth:state:" + TokenUtils.digest(expiring.state()), Duration.ofMillis(1));
        Thread.sleep(5);
        assertCode(callback(expiring, "same"), 400, "OAUTH_STATE_INVALID");
        OAuthFlow next = start("LOGIN", null, flow.browser());
        String expires = callbackTicket(next, "another");
        redis.expire("dl:test:oauth:ticket:" + TokenUtils.digest(expires), Duration.ofMillis(1));
        Thread.sleep(5);
        assertCode(exchange(expires, flow.browser(), null), 400, "OAUTH_STATE_INVALID");
        OAuthFlow denied = start("LOGIN", null, flow.browser());
        MvcResult cancel = mvc.perform(req("GET", "/auth/oauth/GITHUB/callback").param("state", denied.state())
                .param("error", "access_denied").param("error_description", "must-not-echo")
                .cookie(new Cookie("__Host-dl-oauth-browser", flow.browser()))).andReturn();
        assertThat(cancel.getResponse().getHeader("Location")).isEqualTo(ORIGIN + "/oauth/complete#error=OAUTH_DENIED");
        assertThat(store.containsKey(denied.state())).isFalse();
    }

    @Test
    void bindRequiresOriginalTokenAndNeverTransfersOwnership() throws Exception {
        register("leaf");
        register("other");
        String original = login("leaf");
        String sameUserOtherToken = login("leaf");
        String otherUser = login("other");
        OAuthFlow bind = start("BIND", original, null);
        String ticket = callbackTicket(bind, "bind-identity");
        assertCode(exchange(ticket, bind.browser(), otherUser), 400, "OAUTH_STATE_INVALID");
        assertCode(exchange(ticket, bind.browser(), sameUserOtherToken), 400, "OAUTH_STATE_INVALID");
        assertCode(exchange(ticket, bind.browser(), original), 200, "OK");
        assertThat(data(call("GET", "/me/oauth-bindings", original, null)).size()).isEqualTo(1);
        OAuthFlow conflict = start("BIND", otherUser, bind.browser());
        assertThat(callback(conflict, "bind-identity").getResponse().getHeader("Location")).endsWith(
                "#error=OAUTH_BIND_CONFLICT");
        OAuthFlow revoked = start("BIND", original, bind.browser());
        call("POST", "/auth/logout", original, null);
        assertCode(callback(revoked, "new-identity"), 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth", Integer.class)).isEqualTo(1);
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", sameUserOtherToken, null), 200, "OK");
        assertCode(call("GET", "/me", sameUserOtherToken, null), 401, "UNAUTHENTICATED");
    }

    @Test
    void trustedCallbackTransactionFailureRollsBackAndConsumesState() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        String token = login("leaf");
        OAuthFlow bind = start("BIND", token, null);
        // 实际写入绑定后再注入事务异常，检查回滚和跳转，会话校验及 Mapper 都照常执行。
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new TransactionSystemException("injected transaction failure");
        }).when(users).bindOAuth(id, TokenUtils.digest(token), "GITHUB", "transaction-failure-identity");

        MvcResult failed = callback(bind, "transaction-failure-identity");
        assertThat(failed.getResponse().getStatus()).isEqualTo(303);
        assertThat(failed.getResponse().getHeader("Location"))
                .isEqualTo(ORIGIN + "/oauth/complete#error=SERVICE_UNAVAILABLE");
        assertThat(failed.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(failed.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?", Integer.class, id)).isZero();
        assertThat(store.containsKey(bind.state())).isFalse();
        assertThat(redis.keys("dl:test:oauth:ticket:*")).isEmpty();
        assertCode(callback(bind, "transaction-failure-identity"), 400, "OAUTH_STATE_INVALID");
    }

    @Test
    void redisAtomicConsumeMillisecondTimeoutAndRateLimit() throws Exception {
        String state = TokenUtils.random();
        store.save("state", state, "value", Duration.ofSeconds(30));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Boolean>> consumers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                consumers.add(() -> {
                    try {
                        store.consume("state", state, "value");
                        return true;
                    } catch (BusinessException exception) {
                        return false;
                    }
                });
            }
            int winners = 0;
            for (var future : executor.invokeAll(consumers)) {
                if (future.get()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        }
        store.cache(state, state, 30);
        assertThat(
                redis.getExpire("dl:test:oauth:justauth:" + TokenUtils.digest(state), TimeUnit.MILLISECONDS)).isBetween(
                1L, 30L);
        Thread.sleep(45);
        assertThat(store.containsKey(state)).isFalse();
        properties.getRate().setLoginFailures(1);
        try {
            assertCode(call("POST", "/auth/login", null, loginBody("missing", PASSWORD)), 401, "AUTH_FAILED");
            MvcResult limited = call("POST", "/auth/login", null, loginBody("MISSING", PASSWORD));
            assertCode(limited, 429, "RATE_LIMITED");
            assertThat(Integer.parseInt(limited.getResponse().getHeader("Retry-After"))).isPositive();
        } finally {
            properties.getRate().setLoginFailures(10);
        }
    }

    @Test
    void existingMappingsAndExpandedSchemaMatchRealMysql() throws Exception {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class);
        assertThat(tables).hasSize(28);
        // 已经实现的 19 个实体逐列检查，其他业务仍只检查数据库设计。
        tables.removeAll(List.of("home_profile", "home_social_link", "cv_experience", "cv_skill",
                "blog_message", "blog_music", "blog_rss_subscription", "blog_visitor", "blog_page_view"));
        assertThat(tables).hasSize(19);
        for (String table : tables) {
            StringBuilder className = new StringBuilder();
            for (String part : table.split("_")) {
                className.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            Class<?> entity = Class.forName("com.xuan.entity." + className);
            assertThat(entity.getAnnotation(TableName.class).value()).isEqualTo(table);
            Set<String> mapped = new HashSet<>();
            int idCount = 0;
            for (var field : entity.getDeclaredFields()) {
                TableId id = field.getAnnotation(TableId.class);
                TableField column = field.getAnnotation(TableField.class);
                mapped.add(id == null ? column.value() : id.value());
                if (id != null) {
                    idCount++;
                    assertThat(id.type()).isEqualTo(table.equals("blog_article_content") || table.equals(
                            "blog_site_config") ? IdType.INPUT : IdType.AUTO);
                }
            }
            List<String> keys = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND " +
                     "table_name=? AND constraint_name='PRIMARY'",
                    String.class, table);
            assertThat(idCount).isEqualTo(keys.size() == 1 ? 1 : 0);
            assertThat(mapped).containsExactlyInAnyOrderElementsOf(jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?",
                    String.class, table));
        }
        long userId = Long.parseLong(data(register("leaf")).get("userId").asString());
        jdbc.update(
                "INSERT INTO blog_article(author_id,title,slug,view_count) VALUES (?,'test','test'," +
                 "18446744073709551615)",
                userId);
        jdbc.update(
                "INSERT INTO blog_file(uploader_id,original_name,object_key,mime_type,size_bytes) VALUES (?,'test'," +
                 "'test','image/png',18446744073709551615)",
                userId);
        var config = sqlSessionFactory.getConfiguration();
        if (!config.hasMapper(ArticleTestMapper.class)) {
            config.addMapper(ArticleTestMapper.class);
            config.addMapper(FileTestMapper.class);
        }
        try (var session = sqlSessionFactory.openSession()) {
            assertThat(session.getMapper(ArticleTestMapper.class).selectList(null).getFirst().getViewCount()).isEqualTo(
                    new BigInteger("18446744073709551615"));
            assertThat(session.getMapper(FileTestMapper.class).selectList(null).getFirst().getSizeBytes()).isEqualTo(
                    new BigInteger("18446744073709551615"));
        }
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
    }

    /** 实际检查 MySQL 约束、数据保留规则和默认菜单授权。 */
    @Test
    void expandedBusinessConstraintsAndGrantsMatchRealMysql() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 1. 本用例的数据随事务回滚，不影响身份测试清理顺序。
            status.setRollbackOnly();
            jdbc.update("INSERT INTO sys_user(username,nickname) VALUES ('schema-check','schema-check')");
            long userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username='schema-check'", Long.class);
            jdbc.update("INSERT INTO home_profile(user_id,nickname) VALUES (?,'站点主人')", userId);
            assertThatThrownBy(
                    () -> jdbc.update("INSERT INTO home_profile(profile_id,user_id,nickname) VALUES (2,?,'其他资料')",
                            userId))
                    .isInstanceOf(UncategorizedSQLException.class).hasMessageContaining("ck_profile_singleton");
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO cv_experience(experience_type,title,content,start_date,end_date) VALUES (0,'学校'," +
                     "'内容','2026-10-30','2025-10-30')"))
                    .isInstanceOf(UncategorizedSQLException.class).hasMessageContaining("ck_experience_dates");
            jdbc.update("INSERT INTO blog_music(title,audio_url) VALUES ('音乐','https://example.test/audio.mp3')");
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO blog_music(title,audio_url,lyric_url) VALUES ('音乐','https://example.test/audio.mp3'," +
                     "'https://example.test/lyric.lrc')"))
                    .isInstanceOf(UncategorizedSQLException.class).hasMessageContaining("ck_music_lyric");

            // 2. 留言只收本站用户，删除父节点也保留回复链，订阅邮箱要确认且不能重复。
            jdbc.update("INSERT INTO blog_message(user_id,content) VALUES (?,'根留言')", userId);
            long parentId = jdbc.queryForObject("SELECT message_id FROM blog_message WHERE user_id=?", Long.class,
                    userId);
            jdbc.update("INSERT INTO blog_message(user_id,parent_id,content) VALUES (?,?,'回复')", userId, parentId);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM blog_message WHERE message_id=?", parentId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO blog_message(user_id,content) VALUES (-1,'未知账号')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO blog_rss_subscription(user_id,email,status) VALUES (?,'leaf@example.test',1)", userId))
                    .isInstanceOf(UncategorizedSQLException.class).hasMessageContaining("ck_rss_active");
            jdbc.update("INSERT INTO blog_rss_subscription(user_id,email) VALUES (?,'leaf@example.test')", userId);
            jdbc.update("INSERT INTO sys_user(username,nickname) VALUES ('schema-second','schema-second')");
            long secondUser = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username='schema-second'",
                    Long.class);
            assertThatThrownBy(
                    () -> jdbc.update("INSERT INTO blog_rss_subscription(user_id,email) VALUES (?,'LEAF@example.test')",
                            secondUser))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // 3. 清统计标识或账号时要保留浏览和操作日志，统计记录不能拿来当身份凭据。
            jdbc.update("INSERT INTO blog_visitor(fingerprint) VALUES ('schema-visitor')");
            long visitorId = jdbc.queryForObject(
                    "SELECT visitor_id FROM blog_visitor WHERE fingerprint='schema-visitor'", Long.class);
            jdbc.update(
                    "INSERT INTO blog_page_view(visitor_id,user_id,site_code,page_path) VALUES (?,?,'blog'," +
                     "'/articles/test')",
                    visitorId, secondUser);
            jdbc.update(
                    "INSERT INTO sys_operation_log(user_id,operation_type,operation_target,result) VALUES (?," +
                     "'update','schema-check',1)",
                    secondUser);
            jdbc.update("DELETE FROM blog_visitor WHERE visitor_id=?", visitorId);
            jdbc.update("DELETE FROM sys_user WHERE user_id=?", secondUser);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM blog_page_view WHERE visitor_id IS NULL AND user_id IS NULL AND " +
                     "page_path='/articles/test'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_operation_log WHERE user_id IS NULL AND operation_target='schema-check'",
                    Integer.class)).isEqualTo(1);
        });

        // 4. 新增权限明确授予管理员，作者和会员不能自动获得新增全站管理入口。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id=p" +
                 ".permission_id AND rp.role_id=1 WHERE rp.permission_id IS NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id=m.menu_id AND rm.role_id=1 " +
                 "WHERE rm.menu_id IS NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id IN (2,3) AND permission_id>=120 AND " +
                 "permission_id NOT IN (163,164,165,183,184)",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=3", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.menu_id=b.parent_id WHERE b.menu_type='B' AND p" +
                 ".menu_type<>'M'",
                Integer.class)).isZero();
    }

    @Test
    void concurrentIdentityRegistrationAndLastLoginMethodRemainAtomic() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Long>> calls = List.of(
                    () -> users.resolveOAuth("GITHUB", "concurrent", "用户", null),
                    () -> users.resolveOAuth("GITHUB", "concurrent", "用户", null));
            Set<Long> identities = new HashSet<>();
            for (var result : executor.invokeAll(calls)) {
                identities.add(result.get());
            }
            assertThat(identities).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class)).isEqualTo(1);
            long id = Long.parseLong(data(register("leaf")).get("userId").asString());
            jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,'GITHUB','method')", id);
            String token = login("leaf");
            List<Callable<Integer>> changes = List.of(
                    () -> call("DELETE", "/me/password", token,
                            "{\"currentPassword\":\"" + PASSWORD + "\"}").getResponse().getStatus(),
                    () -> call("DELETE", "/me/oauth-bindings/GITHUB", token, null).getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (var result : executor.invokeAll(changes)) {
                statuses.add(result.get());
            }
            assertThat(statuses).contains(200);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT (password_hash IS NOT NULL) + (SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?) FROM " +
                     "sys_user WHERE user_id=?",
                    Integer.class, id, id)).isEqualTo(1);
        }
    }

    @Test
    void sessionUpdatesKeepAbsoluteTtlAndDoNotReviveExpiredKeys() {
        var dao = (SaTokenDaoForRedisTemplate) SaManager.getSaTokenDao();
        dao.set("ttl-test", "before", 30);
        String key = "dl:test:session:ttl-test";
        redis.expire(key, Duration.ofSeconds(10));
        dao.update("ttl-test", "after");
        assertThat(dao.get("ttl-test")).isEqualTo("after");
        assertThat(redis.getExpire(key)).isBetween(1L, 10L);
        dao.update("absent-test", "must-not-create");
        assertThat(dao.get("absent-test")).isNull();
    }

    @Test
    void pendingLoginTicketCannotResurrectAnUnboundIdentity() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,'GITHUB','pending')", id);
        String token = login("leaf");
        OAuthFlow flow = start("LOGIN", null, null);
        String ticket = callbackTicket(flow, "pending");
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 200, "OK");
        assertCode(exchange(ticket, flow.browser(), null), 400, "OAUTH_STATE_INVALID");
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?", Integer.class, id)).isZero();
    }

    @Test
    void concurrentProfilePatchesRejectTheStaleRevision() throws Exception {
        register("leaf");
        String token = login("leaf");
        String revision = call("GET", "/me", token, null).getResponse().getHeader("X-Revision");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<MvcResult>> changes = List.of(
                    () -> patch(token, revision, "{\"nickname\":\"first\"}"),
                    () -> patch(token, revision, "{\"nickname\":\"second\"}"));
            List<Integer> statuses = new ArrayList<>();
            String saved = null;
            for (var result : executor.invokeAll(changes)) {
                MvcResult response = result.get();
                statuses.add(response.getResponse().getStatus());
                if (response.getResponse().getStatus() == 200) {
                    saved = data(response).get("nickname").asString();
                } else {
                    assertCode(response, 412, "REVISION_CONFLICT");
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
            assertThat(data(call("GET", "/me", token, null)).get("nickname").asString()).isEqualTo(saved);
        }
    }

    @Test
    void bindingListFiltersUnavailablePlatformsAndUnbindOnlyChangesTheCurrentUser() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        long otherId = Long.parseLong(data(register("other")).get("userId").asString());
        String token = login("leaf");
        String another = login("leaf");
        String otherToken = login("other");
        for (String source : List.of("GITHUB", "GITEE", "QQ", "UNKNOWN")) {
            jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,?,?)", id, source, "own-identity");
        }
        jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,'GITHUB','other-identity')", otherId);
        MvcResult listed = call("GET", "/me/oauth-bindings", token, null);
        assertCode(listed, 200, "OK");
        assertThat(data(listed).size()).isEqualTo(2);
        assertThat(data(listed).get(0).get("source").asString()).isEqualTo("GITEE");
        assertThat(data(listed).get(1).get("source").asString()).isEqualTo("GITHUB");
        assertThat(data(listed).get(0).size()).isEqualTo(2);
        assertThat(data(listed).get(0).get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(listed.getResponse().getContentAsString()).doesNotContain("uuid", "oauthId", "own-identity");
        assertThat(listed.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        var github = properties.getProviders().get("GITHUB");
        var gitee = properties.getProviders().get("GITEE");
        String giteeSecret = gitee.getClientSecret();
        try {
            github.setEnabled(false);
            gitee.setClientSecret("");
            assertThat(data(call("GET", "/me/oauth-bindings", token, null)).isEmpty()).isTrue();
            assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"" + PASSWORD + "\"}"), 409,
                    "LAST_LOGIN_METHOD");
            assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 404, "NOT_FOUND");
        } finally {
            github.setEnabled(true);
            gitee.setClientSecret(giteeSecret);
        }
        assertCode(call("DELETE", "/me/oauth-bindings/GITEE", otherToken, null), 404, "NOT_FOUND");
        assertCode(call("DELETE", "/me/oauth-bindings/github", token, null), 404, "NOT_FOUND");
        StpUtil.getStpLogic().getTokenSessionByToken(another)
                .set("authenticatedAt", System.currentTimeMillis() - 301_000);
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", another, null), 403, "REAUTH_REQUIRED");
        assertCode(call("DELETE", "/me/password", another, "{\"currentPassword\":\"" + PASSWORD + "\"}"), 403,
                "REAUTH_REQUIRED");
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=? AND source='GITHUB'",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=? AND source='GITHUB'",
                Integer.class, otherId)).isEqualTo(1);
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", another, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", otherToken, null), 200, "OK");
    }

    @Test
    void passwordChangesValidateCurrentCredentialAndRevokeAllSessions() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        String token = login("leaf");
        String another = login("leaf");
        String before = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id);
        for (String body : List.of("{\"newPassword\":\"new-password-2026\"}",
                "{\"newPassword\":\"new-password-2026\",\"currentPassword\":null}")) {
            assertCode(call("PUT", "/me/password", token, body), 400, "VALIDATION_ERROR");
        }
        assertCode(call("PUT", "/me/password", token,
                "{\"newPassword\":\"new-password-2026\",\"currentPassword\":\"wrong-password\"}"), 401, "AUTH_FAILED");
        assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"wrong-password\"}"), 401,
                "AUTH_FAILED");
        assertThat(
                jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isEqualTo(
                before);
        assertCode(call("PUT", "/me/password", token,
                "{\"newPassword\":\" new-password-2026 \",\"currentPassword\":\"" + PASSWORD + "\"}"), 200, "OK");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", another, null), 401, "UNAUTHENTICATED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", PASSWORD)), 401, "AUTH_FAILED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", "new-password-2026")), 401, "AUTH_FAILED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", " new-password-2026 ")), 200, "OK");
    }

    @Test
    void redisRevocationFailureRollsBackPasswordClearAndUnbind() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        String token = login("leaf");
        jdbc.update("INSERT INTO sys_user_oauth(user_id,source,uuid) VALUES (?,'GITHUB','rollback')", id);
        String before = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id);
        doThrow(new RedisConnectionFailureException("injected external failure")).when(sessions).revokeAll(id);
        assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"" + PASSWORD + "\"}"), 503,
                "SERVICE_UNAVAILABLE");
        assertThat(
                jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isEqualTo(
                before);
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?", Integer.class,
                id)).isEqualTo(1);
        assertCode(call("GET", "/me", token, null), 200, "OK");
    }

    @Test
    void permissionsAndMenusCombineEffectiveRolesAndRequireTheWholeAncestorChain() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES (9,'pages','菜单','ALL'),(10," +
                 "'access','权限','SELF')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,9),(?,10)", id, id);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES (10,1),(10,10),(10,11)");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (9,110),(9,1011)");
        String token = login("leaf");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (9,100),(9,200),(9,140)");
        MvcResult menus = call("GET", "/me/menus", token, null);
        assertCode(menus, 200, "OK");
        assertThat(data(menus).size()).isEqualTo(1);
        assertThat(data(menus).get(0).get("children").size()).isEqualTo(1);
        assertThat(data(menus).get(0).get("children").get(0).get("children").get(0).get("menuId").asString()).isEqualTo(
                "1011");
        assertThat(menus.getResponse().getContentAsString()).contains("\"isVisible\":0")
                .doesNotContain("system", "comment");
        JsonNode capabilities = data(call("GET", "/me/capabilities", token, null));
        assertThat(capabilities.get("roleCodes").toString()).isEqualTo("[\"access\",\"member\",\"pages\"]");
        assertThat(capabilities.get("permissionCodes").toString())
                .isEqualTo(
                        "[\"admin:access\",\"blog:article:create\",\"blog:article:like\",\"blog:article:query\"," +
                         "\"blog:comment:create\",\"blog:comment:deleteOwn\","
                                + "\"blog:message:create\",\"blog:message:deleteOwn\",\"blog:message:updateOwn\"," +
                                 "\"blog:rss:subscribe\",\"blog:rss:unsubscribe\"]");
        assertThat(capabilities.get("dataScopes").get("blog:article:query").asString()).isEqualTo("SELF");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES (9,10)");
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("dataScopes").get("blog:article:query")
                .asString()).isEqualTo("ALL");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=10");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("dataScopes")
                .has("blog:article:query")).isFalse();
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=10");
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=100");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(authorization.requireScope(id, "blog:article:query")).isEqualTo("ALL");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=9");
        assertThat(authorization.requireScope(id, "blog:article:query")).isEqualTo("SELF");
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("roleCodes").toString()).isEqualTo(
                "[\"access\",\"member\"]");
    }

    @Test
    void adminUserRoutesRequireBothCurrentPermissions() throws Exception {
        long id = Long.parseLong(data(register("operator")).get("userId").asString());
        String token = login("operator");
        List<String> paths = List.of("/admin/users", "/admin/users/" + id,
                "/admin/users", "/admin/users/" + id, "/admin/users/" + id + "/roles");
        List<String> methods = List.of("GET", "GET", "POST", "PUT", "PUT");
        List<String> bodies = List.of("", "", createUserBody("created"), userBody("叶", 1), "{\"roleIds\":[]}");
        for (int i = 0; i < paths.size(); i++) {
            String body = bodies.get(i).isEmpty() ? null : bodies.get(i);
            assertCode(call(methods.get(i), paths.get(i), null, body), 401, "UNAUTHENTICATED");
            assertCode(call(methods.get(i), paths.get(i), token, body), 403, "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(9,'operator','操作员','SELF')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,9)", id);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,80)");
        assertCode(call("GET", "/admin/users", token, null), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,1)");
        assertCode(call("GET", "/admin/users", token, null), 200, "OK");
        // 有查询权限不等于能创建、修改或授权，入口身份和权限每次都要重查。
        for (int i = 2; i < paths.size(); i++) {
            assertCode(call(methods.get(i), paths.get(i), token, bodies.get(i)), 403, "FORBIDDEN");
        }
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=80");
        assertCode(call("GET", "/admin/users/" + id, token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=80");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=9");
        assertCode(call("GET", "/admin/users", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_role SET status=1 WHERE role_id=9");
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", id);
        assertCode(call("GET", "/admin/users", token, null), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void adminCreatesPasswordUsersWithoutRolesAndRejectsInvalidFields() throws Exception {
        String admin = adminLogin("adminroot");
        MvcResult created = call("POST", "/admin/users", admin, createUserBody("new_user"));
        assertCode(created, 201, "OK");
        String id = data(created).get("userId").asString();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/users/" + id);
        assertThat(data(created).get("roleIds").isEmpty()).isTrue();
        assertThat(data(created).get("status").asInt()).isEqualTo(1);
        assertThat(data(created).get("hasPassword").asBoolean()).isTrue();
        assertThat(data(created).get("lastLoginTime").isNull()).isTrue();
        assertThat(created.getResponse().getContentAsString()).doesNotContain("passwordHash", "uuid", "accessToken");
        String userToken = login("new_user");
        assertCode(call("GET", "/admin/users", userToken, null), 403, "FORBIDDEN");
        String withEmail = createUserBody("email_user").replace("\"nickname\":\"叶\"",
                "\"nickname\":\"  昵称  \",\"email\":\" used@example.test \"");
        MvcResult emailed = call("POST", "/admin/users", admin, withEmail);
        assertCode(emailed, 201, "OK");
        assertThat(data(emailed).get("nickname").asString()).isEqualTo("昵称");
        assertThat(data(emailed).get("email").asString()).isEqualTo("used@example.test");
        assertCode(call("POST", "/admin/users", admin, createUserBody("NEW_USER")), 409, "UNIQUE_CONFLICT");
        assertCode(call("POST", "/admin/users", admin, withEmail.replace("email_user", "email_other")), 409,
                "UNIQUE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='email_other'",
                Integer.class)).isZero();
        String valid = createUserBody("invalid_user");
        for (String extra : List.of("\"roleIds\":[]", "\"status\":null", "\"status\":2", "\"status\":\"1\"",
                "\"status\":1.5",
                "\"email\":\"bad\"", "\"avatar\":\"javascript:bad\"",
                "\"avatar\":\"" + ORIGIN + "/api/v1/admin/files/1/content\"",
                "\"userId\":\"1\"", "\"nickname\":12")) {
            assertCode(call("POST", "/admin/users", admin, valid.substring(0, valid.length() - 1) + "," + extra + "}"),
                    400, "VALIDATION_ERROR");
        }
        assertCode(call("POST", "/admin/users", admin, valid.replace(PASSWORD, "密".repeat(25))), 400,
                "VALIDATION_ERROR");
        assertCode(call("POST", "/admin/users", admin, valid.replace(PASSWORD, "short")), 400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='invalid_user'",
                Integer.class)).isZero();
        MvcResult disabled = call("POST", "/admin/users", admin,
                valid.substring(0, valid.length() - 1) + ",\"status\":0}");
        assertCode(disabled, 201, "OK");
        assertCode(call("POST", "/auth/login", null, loginBody("invalid_user", PASSWORD)), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void adminUserPaginationUsesLiteralFiltersAndStableOrder() throws Exception {
        String admin = adminLogin("adminroot");
        long first = Long.parseLong(data(register("first_user")).get("userId").asString());
        long second = Long.parseLong(data(register("second_user")).get("userId").asString());
        jdbc.update("UPDATE sys_user SET nickname='literal%_\\\\',create_time='2026-01-01' WHERE user_id IN (?,?)",
                first, second);
        jdbc.update("UPDATE sys_user SET email='search@example.test',status=0 WHERE user_id=?", first);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,2)", first);
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=3");
        MvcResult page = mvc.perform(req("GET", "/admin/users").header("Authorization", "Bearer " + admin)
                .param("keyword", "  literal%_  ").param("roleId", "3").param("pageSize", "1")).andReturn();
        assertCode(page, 200, "OK");
        assertThat(page.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(page).get("total").asString()).isEqualTo("2");
        assertThat(data(page).get("totalPages").asString()).isEqualTo("2");
        assertThat(data(page).get("records").get(0).get("userId").asString()).isEqualTo(Long.toString(second));
        MvcResult filtered = mvc.perform(req("GET", "/admin/users").header("Authorization", "Bearer " + admin)
                .param("keyword", "SEARCH@").param("status", "0").param("roleId", "2")).andReturn();
        assertCode(filtered, 200, "OK");
        assertThat(data(filtered).get("total").asString()).isEqualTo("1");
        assertThat(data(filtered).get("records").get(0).get("roleIds").toString()).isEqualTo("[\"2\",\"3\"]");
        MvcResult beyond = mvc.perform(req("GET", "/admin/users").header("Authorization", "Bearer " + admin)
                .param("keyword", "literal%_").param("page", "100000")).andReturn();
        assertCode(beyond, 200, "OK");
        assertThat(data(beyond).get("page").asInt()).isEqualTo(100000);
        assertThat(data(beyond).get("records").isEmpty()).isTrue();
        MvcResult empty = mvc.perform(req("GET", "/admin/users").header("Authorization", "Bearer " + admin)
                .param("keyword", "unmatched")).andReturn();
        assertCode(empty, 200, "OK");
        assertThat(data(empty).get("totalPages").asString()).isEqualTo("0");
        for (String[] invalid : List.of(new String[]{"page", "0"}, new String[]{"page", "100001"},
                new String[]{"pageSize", "101"}, new String[]{"pageSize", "-1"}, new String[]{"status", "2"},
                new String[]{"roleId", "01"}, new String[]{"roleId", "9223372036854775808"},
                new String[]{"keyword", " "}, new String[]{"keyword", "😀".repeat(101)})) {
            assertCode(mvc.perform(req("GET", "/admin/users").header("Authorization", "Bearer " + admin)
                    .param(invalid[0], invalid[1])).andReturn(), 400, "VALIDATION_ERROR");
        }
        for (String invalid : List.of("0", "01", "-1", "1.0", "9223372036854775808")) {
            assertCode(call("GET", "/admin/users/" + invalid, admin, null), 400, "VALIDATION_ERROR");
        }
        assertCode(call("GET", "/admin/users/9223372036854775807", admin, null), 404, "NOT_FOUND");
    }

    @Test
    void adminUpdateReplacesOnlyProfileAndStatusWithSharedRevision() throws Exception {
        String admin = adminLogin("adminroot");
        String id = data(register("target_user")).get("userId").asString();
        String token = login("target_user");
        String another = login("target_user");
        String path = "/admin/users/" + id;
        String revision = adminRevision(admin, id);
        assertThat(call("GET", "/me", token, null).getResponse().getHeader("X-Revision")).isEqualTo(revision);
        assertCode(call("PUT", path, admin, userBody("叶", 1)), 428, "PRECONDITION_REQUIRED");
        assertCode(adminPut(path, admin, "*", userBody("叶", 1)), 400, "VALIDATION_ERROR");
        assertCode(adminPut(path, admin, revision + "," + revision, userBody("叶", 1)), 400, "VALIDATION_ERROR");
        for (String field : List.of("email", "avatar", "intro")) {
            assertCode(adminPut(path, admin, revision, userBody("叶", 1).replace("\"" + field + "\":null,", "")), 400,
                    "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"username\":\"changed\"", "\"password\":\"12345678\"", "\"roleIds\":[]")) {
            String body = userBody("叶", 1);
            assertCode(adminPut(path, admin, revision, body.substring(0, body.length() - 1) + "," + extra + "}"), 400,
                    "VALIDATION_ERROR");
        }
        MvcResult updated = adminPut(path, admin, revision,
                userBody("  修改昵称  ", 1).replace("\"intro\":null", "\"intro\":\"  \""));
        assertCode(updated, 200, "OK");
        assertThat(data(updated).get("nickname").asString()).isEqualTo("修改昵称");
        assertThat(data(updated).get("intro").isNull()).isTrue();
        assertThat(data(updated).get("username").asString()).isEqualTo("target_user");
        assertThat(data(updated).get("roleIds").toString()).isEqualTo("[\"3\"]");
        String current = updated.getResponse().getHeader("X-Revision");
        assertThat(current).isNotEqualTo(revision);
        assertCode(call("GET", "/me", token, null), 200, "OK");
        assertCode(adminPut(path, admin, revision, userBody("stale", 0)), 412, "REVISION_CONFLICT");
        assertCode(adminPut(path, admin, current, userBody("修改昵称", 0)), 200, "OK");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", another, null), 401, "UNAUTHENTICATED");
        assertCode(adminPut(path, admin, adminRevision(admin, id), userBody("修改昵称", 1)), 200, "OK");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", login("target_user"), null), 200, "OK");
        assertCode(adminPut("/admin/users/9223372036854775807", admin, current, userBody("叶", 1)), 404, "NOT_FOUND");
    }

    @Test
    void adminRolesReplaceValidateAndRevokeAllTargetSessions() throws Exception {
        String admin = adminLogin("adminroot");
        String id = data(register("target_user")).get("userId").asString();
        String token = login("target_user");
        String another = login("target_user");
        String revision = adminRevision(admin, id);
        String path = "/admin/users/" + id + "/roles";
        assertCode(call("PUT", path, admin, "{\"roleIds\":[]}"), 428, "PRECONDITION_REQUIRED");
        for (String body : List.of("{}", "{\"roleIds\":null}", "{\"roleIds\":[null]}", "{\"roleIds\":[3]}",
                "{\"roleIds\":[\"03\"]}", "{\"roleIds\":[\"9223372036854775808\"]}", "{\"roleIds\":[\"3\",\"3\"]}",
                "{\"roleIds\":[\"3\",\"999999\"]}", "{\"roleIds\":[],\"status\":1}",
                json.writeValueAsString(
                        Map.of("roleIds", IntStream.range(1, 102).mapToObj(Integer::toString).toList())))) {
            assertCode(adminPut(path, admin, revision, body), 400, "VALIDATION_ERROR");
        }
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=2");
        assertCode(adminPut(path, admin, revision, "{\"roleIds\":[\"2\",\"3\"]}"), 400, "VALIDATION_ERROR");
        assertThat(adminRevision(admin, id)).isEqualTo(revision);
        jdbc.update("UPDATE sys_role SET status=1 WHERE role_id=2");
        MvcResult assigned = adminPut(path, admin, revision, "{\"roleIds\":[\"3\",\"2\"]}");
        assertCode(assigned, 200, "OK");
        assertThat(data(assigned).get("roleIds").toString()).isEqualTo("[\"2\",\"3\"]");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", another, null), 401, "UNAUTHENTICATED");
        token = login("target_user");
        assertCode(call("GET", "/admin/test/query", token, null), 200, "OK");
        String current = assigned.getResponse().getHeader("X-Revision");
        MvcResult noop = adminPut(path, admin, current, "{\"roleIds\":[\"2\",\"3\"]}");
        assertCode(noop, 200, "OK");
        assertThat(noop.getResponse().getHeader("X-Revision")).isEqualTo(current);
        assertCode(call("GET", "/me", token, null), 200, "OK");
        assertCode(adminPut("/admin/users/" + id, admin, revision, userBody("stale", 1)), 412, "REVISION_CONFLICT");
        assertCode(adminPut(path, admin, current, "{\"roleIds\":[]}"), 200, "OK");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        token = login("target_user");
        assertCode(call("GET", "/admin/test/query", token, null), 403, "FORBIDDEN");
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("permissionCodes").isEmpty()).isTrue();
    }

    @Test
    void adminLastAdministratorProtectionRollsBackBothKindsOfChange() throws Exception {
        String admin = adminLogin("adminroot");
        String id = data(call("GET", "/me", admin, null)).get("userId").asString();
        String other = data(register("disabled_admin")).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", other);
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", other);
        String revision = adminRevision(admin, id);
        try (var sql = sqlSessionFactory.openSession()) {
            assertThat(sql.getMapper(UserMapper.class).effectiveAdminCount(jdbc.queryForList(
                    "SELECT permission_code FROM sys_permission WHERE permission_code='admin:access' OR " +
                     "permission_code LIKE 'sys:%'",
                    String.class))).isEqualTo(1);
        }
        assertCode(adminPut("/admin/users/" + id, admin, revision, userBody("不应保存", 0)), 409, "LAST_ADMIN");
        assertCode(adminPut("/admin/users/" + id + "/roles", admin, revision, "{\"roleIds\":[\"3\"]}"), 409,
                "LAST_ADMIN");
        assertThat(adminRevision(admin, id)).isEqualTo(revision);
        assertThat(data(call("GET", "/me", admin, null)).get("nickname").asString()).isEqualTo("叶");
        assertThat(data(call("GET", "/admin/users/" + id, admin, null)).get("roleIds").toString()).contains("\"1\"");
    }

    @Test
    void adminRedisFailureAndUniqueConflictRollBackUserAndRoleWrites() throws Exception {
        String admin = adminLogin("adminroot");
        String id = data(register("target_user")).get("userId").asString();
        String token = login("target_user");
        String revision = adminRevision(admin, id);
        doThrow(new RedisConnectionFailureException("injected external failure")).when(sessions)
                .revokeAll(Long.parseLong(id));
        assertCode(adminPut("/admin/users/" + id, admin, revision, userBody("不应保存", 0)), 503,
                "SERVICE_UNAVAILABLE");
        assertCode(adminPut("/admin/users/" + id + "/roles", admin, revision, "{\"roleIds\":[\"2\"]}"), 503,
                "SERVICE_UNAVAILABLE");
        assertThat(adminRevision(admin, id)).isEqualTo(revision);
        assertThat(data(call("GET", "/admin/users/" + id, admin, null)).get("roleIds").toString()).isEqualTo("[\"3\"]");
        assertCode(call("GET", "/me", token, null), 200, "OK");
        reset(sessions);
        jdbc.update("UPDATE sys_user SET email='used@example.test' WHERE username='adminroot'");
        assertCode(adminPut("/admin/users/" + id, admin, revision,
                        userBody("不应保存", 0).replace("\"email\":null", "\"email\":\"USED@example.test\"")), 409,
                "UNIQUE_CONFLICT");
        assertThat(adminRevision(admin, id)).isEqualTo(revision);
        assertCode(call("GET", "/me", token, null), 200, "OK");
    }

    @Test
    void concurrentAdminProfileAndRolesUseOneAggregateRevision() throws Exception {
        String admin = adminLogin("adminroot");
        String id = data(register("target_user")).get("userId").asString();
        String revision = adminRevision(admin, id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> changes = List.of(
                    () -> adminPut("/admin/users/" + id, admin, revision, userBody("并发资料", 1)),
                    () -> adminPut("/admin/users/" + id + "/roles", admin, revision, "{\"roleIds\":[\"2\"]}"));
            List<Integer> statuses = new ArrayList<>();
            for (var result : executor.invokeAll(changes)) {
                statuses.add(result.get(10, TimeUnit.SECONDS).getResponse().getStatus());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        }
    }

    @Test
    void concurrentAdminDisableAndRoleRemovalCannotCloseManagement() throws Exception {
        String first = adminLogin("first_admin");
        String second = adminLogin("second_admin");
        String firstId = data(call("GET", "/me", first, null)).get("userId").asString();
        String secondId = data(call("GET", "/me", second, null)).get("userId").asString();
        String firstRevision = adminRevision(first, firstId);
        String secondRevision = adminRevision(second, secondId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> changes = List.of(
                    () -> adminPut("/admin/users/" + firstId, first, firstRevision, userBody("叶", 0)),
                    () -> adminPut("/admin/users/" + secondId + "/roles", second, secondRevision, "{\"roleIds\":[]}"));
            List<Integer> statuses = new ArrayList<>();
            for (var result : executor.invokeAll(changes)) {
                MvcResult response = result.get(10, TimeUnit.SECONDS);
                statuses.add(response.getResponse().getStatus());
                assertCode(response, response.getResponse().getStatus(),
                        response.getResponse().getStatus() == 200 ? "OK" : "LAST_ADMIN");
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON u.user_id=ur.user_id WHERE u.status=1 AND " +
                 "ur.role_id=1",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void adminWriteRechecksPermissionAndTokenAfterWaitingForGuard() throws Exception {
        String admin = adminLogin("adminroot");
        long actor = Long.parseLong(data(call("GET", "/me", admin, null)).get("userId").asString());
        String target = data(register("target_user")).get("userId").asString();
        String revision = adminRevision(admin, target);
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (boolean revoke : List.of(false, true)) {
                CountDownLatch entered = new CountDownLatch(1);
                doAnswer(invocation -> {
                    entered.countDown();
                    return invocation.callRealMethod();
                }).when(AopTestUtils.<IUserService>getUltimateTargetObject(users))
                        .adminUpdate(eq(Long.parseLong(target)), any(AdminUserUpdateDTO.class), eq(revision));
                // 主线程先拿真实保护锁，另一个请求通过 MVC 检查后进入业务层，再等这把锁。
                var pending = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_id=1 FOR UPDATE", Long.class);
                    var response = executor.submit(
                            () -> adminPut("/admin/users/" + target, admin, revision, userBody("不应保存", 1)));
                    try {
                        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_id=1", actor);
                    if (revoke) {
                        sessions.revokeAll(actor);
                    }
                    return response;
                });
                assertCode(pending.get(10, TimeUnit.SECONDS), revoke ? 401 : 403,
                        revoke ? "UNAUTHENTICATED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT nickname FROM sys_user WHERE user_id=?", String.class,
                        target)).isEqualTo("叶");
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", actor);
            }
        }
    }

    @Test
    void roleRoutesRequireBothEffectivePermissionsAndCurrentIdentity() throws Exception {
        String id = data(register("operator")).get("userId").asString();
        String token = login("operator");
        List<String> paths = List.of("/admin/roles", "/admin/roles/2", "/admin/roles",
                "/admin/roles/2", "/admin/roles/2", "/admin/roles/2/grants");
        List<String> methods = List.of("GET", "GET", "POST", "PUT", "DELETE", "PUT");
        List<String> bodies = List.of("", "", roleCreateBody("editor"), roleBody("作者", "SELF", 1), "",
                grantBody(List.of(), List.of()));
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name) VALUES(9,'operator','操作员')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,9)", id);
        for (int i = 0; i < paths.size(); i++) {
            String body = bodies.get(i).isEmpty() ? null : bodies.get(i);
            assertCode(call(methods.get(i), paths.get(i), null, body), 401, "UNAUTHENTICATED");
            assertCode(call(methods.get(i), paths.get(i), token, body), 403, "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 9,permission_id FROM sys_permission " +
                 "WHERE permission_code LIKE 'sys:role:%'");
        for (int i = 0; i < paths.size(); i++) {
            assertCode(call(methods.get(i), paths.get(i), token, bodies.get(i).isEmpty() ? null : bodies.get(i)), 403,
                    "FORBIDDEN");
        }
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=9");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,1)");
        for (int i = 0; i < paths.size(); i++) {
            assertCode(call(methods.get(i), paths.get(i), token, bodies.get(i).isEmpty() ? null : bodies.get(i)), 403,
                    "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 9,permission_id FROM sys_permission " +
                 "WHERE permission_code='sys:role:query'");
        assertCode(call("GET", "/admin/roles", token, null), 200, "OK");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='sys:role:query'");
        assertCode(call("GET", "/admin/roles", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_code='sys:role:query'");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=9");
        assertCode(call("GET", "/admin/roles", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", id);
        assertCode(call("GET", "/admin/roles", token, null), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void roleCreateDefaultsWhitelistAndLiteralPagination() throws Exception {
        String admin = adminLogin("adminroot");
        MvcResult created = call("POST", "/admin/roles", admin, roleCreateBody("editor"));
        assertCode(created, 201, "OK");
        String id = data(created).get("roleId").asString();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/roles/" + id);
        assertThat(created.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(created).get("dataScope").asString()).isEqualTo("SELF");
        assertThat(data(created).get("status").asInt()).isEqualTo(1);
        assertThat(data(created).get("description").isNull()).isTrue();
        assertThat(data(created).get("permissionIds").isEmpty()).isTrue();
        assertThat(data(created).get("menuIds").isEmpty()).isTrue();
        assertThat(data(created).get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertCode(call("POST", "/admin/roles", admin, roleCreateBody("editor")), 409, "UNIQUE_CONFLICT");
        for (String body : List.of("{}", roleCreateBody("Editor"), roleCreateBody("a-b"),
                roleCreateBody("a".repeat(65)), "{\"roleCode\":\"x\",\"roleName\":\" \"}",
                "{\"roleCode\":\"x\",\"roleName\":\"😀\"}".replace("😀", "😀".repeat(65)),
                roleCreateBody("x").replace("\"内容编辑\"", "12"),
                roleCreateBody("x").replace("}", ",\"roleCode\":\"y\"}"))) {
            assertCode(call("POST", "/admin/roles", admin, body), 400, "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"permissionIds\":[]", "\"menuIds\":[]", "\"roleId\":\"1\"",
                "\"dataScope\":null", "\"dataScope\":\"all\"", "\"status\":null", "\"status\":1.2",
                "\"status\":\"1\"", "\"status\":2", "\"description\":\"" + "中".repeat(256) + "\"")) {
            assertCode(call("POST", "/admin/roles", admin, roleCreateBody("x").replace("}", "," + extra + "}")), 400,
                    "VALIDATION_ERROR");
        }
        call("POST", "/admin/roles", admin, roleCreateBody("under_score").replace("内容编辑", "字面%_\\\\匹配"));
        MvcResult second = call("POST", "/admin/roles", admin,
                roleCreateBody("another").replace("内容编辑", "字面%_\\\\匹配").replace("}", ",\"status\":0}"));
        MvcResult page = mvc.perform(req("GET", "/admin/roles").header("Authorization", "Bearer " + admin)
                .param("keyword", " %_\\ ").param("pageSize", "1")).andReturn();
        assertCode(page, 200, "OK");
        assertThat(data(page).get("total").asString()).isEqualTo("2");
        assertThat(data(page).get("totalPages").asString()).isEqualTo("2");
        assertThat(data(page).get("records").get(0).get("roleCode").asString()).isEqualTo("under_score");
        assertThat(page.getResponse().getHeader("X-Revision")).isNull();
        MvcResult filtered = mvc.perform(req("GET", "/admin/roles").header("Authorization", "Bearer " + admin)
                .param("keyword", "%_\\").param("status", "0")).andReturn();
        assertThat(data(filtered).get("total").asString()).isEqualTo("1");
        assertThat(data(filtered).get("records").get(0).get("roleId")).isEqualTo(data(second).get("roleId"));
        MvcResult beyond = call("GET", "/admin/roles?page=100000", admin, null);
        assertThat(data(beyond).get("page").asInt()).isEqualTo(100000);
        assertThat(data(beyond).get("records").isEmpty()).isTrue();
        assertThat(data(call("GET", "/admin/roles?keyword=editor", admin, null)).get("total").asString()).isEqualTo(
                "1");
        for (String query : List.of("page=0", "page=100001", "pageSize=101", "pageSize=1.2", "status=2", "keyword= ")) {
            String[] parameter = query.split("=", 2);
            assertCode(mvc.perform(req("GET", "/admin/roles").header("Authorization", "Bearer " + admin)
                    .param(parameter[0], parameter[1])).andReturn(), 400, "VALIDATION_ERROR");
        }
        for (String invalid : List.of("01", "0", "-1", "1.2", "9223372036854775808")) {
            assertCode(call("GET", "/admin/roles/" + invalid, admin, null), 400, "VALIDATION_ERROR");
        }
        assertCode(call("GET", "/admin/roles/9223372036854775807", admin, null), 404, "NOT_FOUND");
    }

    @Test
    void roleUpdateReplacesFieldsAndRevokesOnlyAuthorizationChanges() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String user = data(register("role_user")).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, id);
        String first = login("role_user");
        String second = login("role_user");
        String path = "/admin/roles/" + id;
        String revision = roleRevision(admin, id);
        assertCode(call("PUT", path, admin, roleBody("编辑", "SELF", 1)), 428, "PRECONDITION_REQUIRED");
        for (String invalid : List.of("*", "revision", revision + "," + revision)) {
            assertCode(adminPut(path, admin, invalid, roleBody("编辑", "SELF", 1)), 400, "VALIDATION_ERROR");
        }
        for (String field : List.of("\"description\":null,", "\"dataScope\":\"SELF\",", ",\"status\":1")) {
            assertCode(adminPut(path, admin, revision, roleBody("编辑", "SELF", 1).replace(field, "")), 400,
                    "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"roleCode\":\"changed\"", "\"permissionIds\":[]", "\"menuIds\":[]",
                "\"updateTime\":null")) {
            assertCode(adminPut(path, admin, revision, roleBody("编辑", "SELF", 1).replace("}", "," + extra + "}")),
                    400, "VALIDATION_ERROR");
        }
        MvcResult updated = adminPut(path, admin, revision, roleBody("  编辑  ", "SELF", 1).replace("null", "\"   \""));
        assertCode(updated, 200, "OK");
        assertThat(data(updated).get("roleName").asString()).isEqualTo("编辑");
        assertThat(data(updated).get("roleCode").asString()).isEqualTo("editor");
        assertThat(data(updated).get("description").isNull()).isTrue();
        assertCode(call("GET", "/me", first, null), 200, "OK");
        assertCode(call("GET", "/me", second, null), 200, "OK");
        assertCode(adminPut(path, admin, revision, roleBody("旧版本", "ALL", 1)), 412, "REVISION_CONFLICT");
        String current = updated.getResponse().getHeader("X-Revision");
        assertThat(current).isNotEqualTo(revision);
        assertCode(adminPut(path, admin, current, roleBody("编辑", "ALL", 1)), 200, "OK");
        assertCode(call("GET", "/me", first, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", second, null), 401, "UNAUTHENTICATED");
        first = login("role_user");
        assertCode(adminPut(path, admin, roleRevision(admin, id), roleBody("编辑", "ALL", 0)), 200, "OK");
        assertCode(call("GET", "/me", first, null), 401, "UNAUTHENTICATED");
        assertCode(adminPut(path, admin, roleRevision(admin, id), roleBody("编辑", "ALL", 1)), 200, "OK");
        assertCode(call("GET", "/me", first, null), 401, "UNAUTHENTICATED");
    }

    @Test
    void roleGrantsValidateBothSetsAndCompleteAncestorsWithoutImplicitPermissions() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String path = "/admin/roles/" + id + "/grants";
        String original = roleRevision(admin, id);
        long button = jdbc.queryForObject("SELECT menu_id FROM sys_menu WHERE menu_type='B' AND permission_id=11",
                Long.class);
        assertCode(call("PUT", path, admin, grantBody(List.of(), List.of())), 428, "PRECONDITION_REQUIRED");
        for (String body : List.of("{}", "{\"permissionIds\":[],\"menuIds\":null}",
                "{\"permissionIds\":[1],\"menuIds\":[]}", "{\"permissionIds\":[\"01\"],\"menuIds\":[]}",
                "{\"permissionIds\":[null],\"menuIds\":[]}", "{\"permissionIds\":[\"1\",\"1\"],\"menuIds\":[]}",
                "{\"permissionIds\":[],\"menuIds\":[\"100\",\"100\"]}",
                grantBody(List.of(99999L), List.of()), grantBody(List.of(10L), List.of(99999L)),
                grantBody(List.of(), List.of(110L)), grantBody(List.of(11L), List.of(button)),
                grantBody(IntStream.rangeClosed(1, 1001).mapToObj(i -> (long) i).toList(), List.of()))) {
            assertCode(adminPut(path, admin, original, body), 400, "VALIDATION_ERROR");
            assertThat(roleRevision(admin, id)).isEqualTo(original);
        }
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=100");
        assertCode(adminPut(path, admin, original, grantBody(List.of(10L), List.of(110L))), 400, "VALIDATION_ERROR");
        jdbc.update("UPDATE sys_menu SET status=1 WHERE menu_id=100");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=10");
        assertCode(adminPut(path, admin, original, grantBody(List.of(10L), List.of(110L))), 400, "VALIDATION_ERROR");
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=10");
        MvcResult granted = adminPut(path, admin, original, grantBody(List.of(11L, 10L, 1L, 45L), List.of(button)));
        assertCode(granted, 200, "OK");
        assertThat(data(granted).get("permissionIds").toString()).isEqualTo("[\"1\",\"10\",\"11\",\"45\"]");
        assertThat(data(granted).get("menuIds").toString()).isEqualTo(
                json.writeValueAsString(List.of(100L, 110L, button).stream().sorted().toList()));
        String current = granted.getResponse().getHeader("X-Revision");
        assertThat(current).isNotEqualTo(original);
        assertCode(adminPut("/admin/roles/" + id, admin, original, roleBody("旧资料", "SELF", 1)), 412,
                "REVISION_CONFLICT");
        // 禁用关联也要出现在详情和版本里，只改时间不改变整体版本。
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=11");
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=?", button);
        assertThat(roleRevision(admin, id)).isEqualTo(current);
        assertThat(data(call("GET", "/admin/roles/" + id, admin, null)).get("permissionIds").toString()).contains(
                "\"11\"");
        assertThat(data(call("GET", "/admin/roles", admin, null)).get("records").toString()).contains(
                "\"" + button + "\"");
        jdbc.update("UPDATE sys_role SET update_time=DATE_ADD(update_time,INTERVAL 1 SECOND) WHERE role_id=?", id);
        assertThat(roleRevision(admin, id)).isEqualTo(current);
        assertCode(adminPut(path, admin, current, grantBody(List.of(), List.of())), 200, "OK");
        assertThat(data(call("GET", "/admin/roles/" + id, admin, null)).get("menuIds").isEmpty()).isTrue();
    }

    @Test
    void roleGrantsRevokeAllUsersAndAreIdempotentForEquivalentFinalSets() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String firstId = data(register("first_user")).get("userId").asString();
        String secondId = data(register("second_user")).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?),(?,?)", firstId, id, secondId, id);
        String first = login("first_user");
        String another = login("first_user");
        String second = login("second_user");
        String path = "/admin/roles/" + id + "/grants";
        MvcResult saved = adminPut(path, admin, roleRevision(admin, id), grantBody(List.of(10L, 1L), List.of(110L)));
        assertCode(saved, 200, "OK");
        for (String token : List.of(first, another, second)) {
            assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        }
        first = login("first_user");
        assertThat(authorization.requireScope(Long.parseLong(firstId), "blog:article:query")).isEqualTo("SELF");
        assertThat(call("GET", "/me/menus", first, null).getResponse().getContentAsString()).contains(
                "content/article/index");
        String revision = saved.getResponse().getHeader("X-Revision");
        MvcResult noop = adminPut(path, admin, revision, grantBody(List.of(1L, 10L), List.of(110L, 100L)));
        assertCode(noop, 200, "OK");
        assertThat(noop.getResponse().getHeader("X-Revision")).isEqualTo(revision);
        assertCode(call("GET", "/me", first, null), 200, "OK");
        assertCode(adminPut(path, admin, revision, grantBody(List.of(), List.of())), 200, "OK");
        assertCode(call("GET", "/me", first, null), 401, "UNAUTHENTICATED");
        first = login("first_user");
        assertCode(call("GET", "/admin/test/query", first, null), 403, "FORBIDDEN");
        assertThat(data(call("GET", "/me/menus", first, null)).isEmpty()).isTrue();
    }

    @Test
    void roleDeletionCascadesUsedCustomRoleAndRollsBackOnRedisFailure() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String path = "/admin/roles/" + id;
        assertCode(
                adminPut(path + "/grants", admin, roleRevision(admin, id), grantBody(List.of(1L, 10L), List.of(110L))),
                200, "OK");
        String user = data(register("role_user")).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, id);
        String token = login("role_user");
        String second = login("role_user");
        String userRevision = adminRevision(admin, user);
        String revision = roleRevision(admin, id);
        assertCode(call("DELETE", path, admin, null), 428, "PRECONDITION_REQUIRED");
        assertCode(roleDelete(path, admin, "*"), 400, "VALIDATION_ERROR");
        assertCode(roleDelete(path, admin, "\"" + "0".repeat(64) + "\""), 412, "REVISION_CONFLICT");
        doThrow(new RedisConnectionFailureException("injected failure")).when(sessions).revokeAll(Long.parseLong(user));
        assertCode(roleDelete(path, admin, revision), 503, "SERVICE_UNAVAILABLE");
        assertThat(roleRevision(admin, id)).isEqualTo(revision);
        assertThat(adminRevision(admin, user)).isEqualTo(userRevision);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class,
                id)).isEqualTo(2);
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=?", Integer.class, id)).isEqualTo(
                2);
        reset(sessions);
        MvcResult deleted = roleDelete(path, admin, revision);
        assertCode(deleted, 200, "OK");
        assertThat(data(deleted).isNull()).isTrue();
        assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        assertCode(call("GET", path, admin, null), 404, "NOT_FOUND");
        for (String table : List.of("sys_role_permission", "sys_role_menu", "sys_user_role")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE role_id=?", Integer.class,
                    id)).isZero();
        }
        assertThat(adminRevision(admin, user)).isNotEqualTo(userRevision);
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", second, null), 401, "UNAUTHENTICATED");
        assertCode(roleDelete(path, admin, revision), 404, "NOT_FOUND");
        for (String builtin : List.of("1", "2", "3")) {
            assertCode(roleDelete("/admin/roles/" + builtin, admin, roleRevision(admin, builtin)), 409,
                    "STATE_CONFLICT");
        }
    }

    @Test
    void roleProtectionRejectsAdminDisableScopeAndMissingInitialPermission() throws Exception {
        String admin = adminLogin("adminroot");
        String revision = roleRevision(admin, "1");
        for (String body : List.of(roleBody("不可停用", "ALL", 0), roleBody("不可缩权", "SELF", 1))) {
            assertCode(adminPut("/admin/roles/1", admin, revision, body), 409, "LAST_ADMIN");
            assertThat(roleRevision(admin, "1")).isEqualTo(revision);
        }
        List<Long> permissions = jdbc.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id=1 ORDER BY permission_id", Long.class);
        List<Long> menus = jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=1 ORDER BY menu_id",
                Long.class);
        assertCode(adminPut("/admin/roles/1/grants", admin, revision, grantBody(List.of(), List.of())), 409,
                "LAST_ADMIN");
        assertCode(adminPut("/admin/roles/1/grants", admin, revision,
                grantBody(permissions.stream().filter(id -> id != 90L).toList(), List.of())), 409, "LAST_ADMIN");
        assertThat(roleRevision(admin, "1")).isEqualTo(revision);
        assertThat(jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=1 ORDER BY menu_id",
                Long.class)).isEqualTo(menus);
        assertCode(call("GET", "/me", admin, null), 200, "OK");
        assertCode(adminPut("/admin/roles/1", admin, revision, roleBody("管理员展示名", "ALL", 1)), 200, "OK");
        assertCode(call("GET", "/me", admin, null), 200, "OK");
    }

    @Test
    void roleRedisFailureRollsBackStatusScopeAndBothGrantCollections() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String user = data(register("role_user")).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, id);
        String token = login("role_user");
        String revision = roleRevision(admin, id);
        doThrow(new RedisConnectionFailureException("injected failure")).when(sessions).revokeAll(Long.parseLong(user));
        assertCode(adminPut("/admin/roles/" + id, admin, revision, roleBody("不应保存", "ALL", 0)), 503,
                "SERVICE_UNAVAILABLE");
        assertCode(
                adminPut("/admin/roles/" + id + "/grants", admin, revision, grantBody(List.of(1L, 10L), List.of(110L))),
                503, "SERVICE_UNAVAILABLE");
        assertThat(roleRevision(admin, id)).isEqualTo(revision);
        assertThat(data(call("GET", "/admin/roles/" + id, admin, null)).get("permissionIds").isEmpty()).isTrue();
        assertThat(data(call("GET", "/admin/roles/" + id, admin, null)).get("menuIds").isEmpty()).isTrue();
        assertCode(call("GET", "/me", token, null), 200, "OK");
    }

    @Test
    void concurrentRoleProfileAndGrantsShareOneRevision() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String revision = roleRevision(admin, id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> writes = List.of(
                    () -> adminPut("/admin/roles/" + id, admin, revision, roleBody("并发资料", "SELF", 1)),
                    () -> adminPut("/admin/roles/" + id + "/grants", admin, revision,
                            grantBody(List.of(1L), List.of())));
            var results = executor.invokeAll(writes);
            assertThat(results.stream().map(result -> {
                try {
                    return result.get().getResponse().getStatus();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).sorted().toList()).containsExactly(200, 412);
        }
    }

    @Test
    void roleGrantsRejectDisabledNodesInvalidParentTypesAndCycles() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createRole(admin, "editor");
        String path = "/admin/roles/" + id + "/grants";
        String revision = roleRevision(admin, id);
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=110");
        assertCode(adminPut(path, admin, revision, grantBody(List.of(10L), List.of(110L))), 400, "VALIDATION_ERROR");
        jdbc.update("UPDATE sys_menu SET status=1 WHERE menu_id=110");
        try {
            jdbc.update("UPDATE sys_menu SET parent_id=120 WHERE menu_id=110");
            assertCode(adminPut(path, admin, revision, grantBody(List.of(10L, 20L), List.of(110L))), 400,
                    "VALIDATION_ERROR");
        } finally {
            jdbc.update("UPDATE sys_menu SET parent_id=100 WHERE menu_id=110");
        }
        try {
            jdbc.update("UPDATE sys_menu SET parent_id=200 WHERE menu_id=100");
            jdbc.update("UPDATE sys_menu SET parent_id=100 WHERE menu_id=200");
            assertCode(adminPut(path, admin, revision, grantBody(List.of(10L), List.of(110L))), 400,
                    "VALIDATION_ERROR");
        } finally {
            jdbc.update("UPDATE sys_menu SET parent_id=NULL WHERE menu_id IN (100,200)");
        }
        assertThat(roleRevision(admin, id)).isEqualTo(revision);
    }

    @Test
    void concurrentRoleAndUserChangesPreserveLastEffectiveAdmin() throws Exception {
        String first = adminLogin("first_admin");
        String second = adminLogin("second_admin");
        String user = data(call("GET", "/me", first, null)).get("userId").asString();
        String userRevision = adminRevision(first, user);
        String roleRevision = roleRevision(second, "1");
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> writes = List.of(
                    () -> adminPut("/admin/users/" + user, first, userRevision, userBody("叶", 0)),
                    () -> adminPut("/admin/roles/1", second, roleRevision, roleBody("管理员", "SELF", 1)));
            var results = executor.invokeAll(writes);
            assertCode(results.get(0).get(), 200, "OK");
            assertCode(results.get(1).get(), 409, "LAST_ADMIN");
        }
        assertThat(jdbc.queryForObject("SELECT data_scope FROM sys_role WHERE role_id=1", String.class)).isEqualTo(
                "ALL");
        assertCode(call("GET", "/admin/roles", second, null), 200, "OK");
    }

    @Test
    void roleWriteRechecksIdentityAndPermissionsAfterWaitingForGuard() throws Exception {
        String admin = adminLogin("adminroot");
        long actor = Long.parseLong(data(call("GET", "/me", admin, null)).get("userId").asString());
        String id = createRole(admin, "editor");
        String revision = roleRevision(admin, id);
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (boolean revoke : List.of(false, true)) {
                CountDownLatch entered = new CountDownLatch(1);
                doAnswer(invocation -> {
                    entered.countDown();
                    return invocation.callRealMethod();
                }).when(AopTestUtils.<IRoleService>getUltimateTargetObject(roles))
                        .update(eq(Long.parseLong(id)), any(RoleUpdateDTO.class), eq(revision));
                var pending = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_id=1 FOR UPDATE", Long.class);
                    var response = executor.submit(
                            () -> adminPut("/admin/roles/" + id, admin, revision, roleBody("不应保存", "SELF", 1)));
                    try {
                        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_id=1", actor);
                    if (revoke) {
                        sessions.revokeAll(actor);
                    }
                    return response;
                });
                assertCode(pending.get(10, TimeUnit.SECONDS), revoke ? 401 : 403,
                        revoke ? "UNAUTHENTICATED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT role_name FROM sys_role WHERE role_id=?", String.class,
                        id)).isEqualTo("内容编辑");
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", actor);
            }
        }
    }

    @Test
    void permissionRoutesRequireBothEffectivePermissionsAndCurrentIdentity() throws Exception {
        String id = data(register("operator")).get("userId").asString();
        String token = login("operator");
        List<String> paths = List.of("/admin/permissions", "/admin/permissions/10", "/admin/permissions",
                "/admin/permissions/10", "/admin/permissions/10");
        List<String> methods = List.of("GET", "GET", "POST", "PUT", "DELETE");
        List<String> bodies = List.of("", "", permissionCreateBody("test:article:export"),
                permissionBody("文章", "blog", 1), "");
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name) VALUES(9,'operator','操作员')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,9)", id);
        for (int i = 0; i < paths.size(); i++) {
            String body = bodies.get(i).isEmpty() ? null : bodies.get(i);
            assertCode(call(methods.get(i), paths.get(i), null, body), 401, "UNAUTHENTICATED");
            assertCode(call(methods.get(i), paths.get(i), token, body), 403, "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 9,permission_id FROM sys_permission " +
                 "WHERE permission_code LIKE 'sys:permission:%'");
        for (int i = 0; i < paths.size(); i++) {
            assertCode(call(methods.get(i), paths.get(i), token, bodies.get(i).isEmpty() ? null : bodies.get(i)), 403,
                    "FORBIDDEN");
        }
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=9");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,1)");
        for (int i = 0; i < paths.size(); i++) {
            assertCode(call(methods.get(i), paths.get(i), token, bodies.get(i).isEmpty() ? null : bodies.get(i)), 403,
                    "FORBIDDEN");
        }
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,110)");
        assertCode(call("GET", "/admin/permissions", token, null), 200, "OK");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=110");
        assertCode(call("GET", "/admin/permissions", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=110");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=9");
        assertCode(call("GET", "/admin/permissions", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", id);
        assertCode(call("GET", "/admin/permissions", token, null), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void permissionCreationDefaultsWhitelistAndStrictInput() throws Exception {
        String admin = adminLogin("adminroot");
        MvcResult created = call("POST", "/admin/permissions", admin, permissionCreateBody("test:article:deleteOwn"));
        assertCode(created, 201, "OK");
        String id = data(created).get("permissionId").asString();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/permissions/" + id);
        assertThat(created.getResponse().getHeader("X-Revision")).isNull();
        assertThat(created.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(data(created).get("description").isNull()).isTrue();
        assertThat(data(created).get("status").asInt()).isEqualTo(1);
        assertThat(data(created).get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id=?", Integer.class,
                id)).isZero();
        assertThat(data(call("GET", "/me/capabilities", admin, null)).get("permissionCodes").toString()).doesNotContain(
                "test:article:deleteOwn");
        assertCode(call("POST", "/admin/permissions", admin, permissionCreateBody("test:article:deleteOwn")), 409,
                "UNIQUE_CONFLICT");
        for (String code : List.of("test", "test:access", "Test:article:query", "test:a_b:query", "test:article:Query",
                "test:资源:query", "test:a:" + "a".repeat(100))) {
            assertCode(call("POST", "/admin/permissions", admin, permissionCreateBody(code)), 400, "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"permissionId\":\"999\"", "\"roleIds\":[]", "\"createTime\":null",
                "\"status\":null", "\"status\":\"1\"", "\"status\":1.5", "\"status\":true", "\"status\":2",
                "\"module\":\"Test\"", "\"module\":\"test\"")) {
            assertCode(call("POST", "/admin/permissions", admin,
                            permissionCreateBody("test:article:other").replace("}", "," + extra + "}")), 400,
                    "VALIDATION_ERROR");
        }
        for (String missing : List.of("\"permissionCode\":\"test:article:other\",", "\"permissionName\":\"导出文章\",",
                ",\"module\":\"test\"")) {
            assertCode(call("POST", "/admin/permissions", admin,
                    permissionCreateBody("test:article:other").replace(missing, "")), 400, "VALIDATION_ERROR");
        }
        assertCode(call("POST", "/admin/permissions", admin, permissionCreateBody("test:article:unicode")
                .replace("导出文章", "😀".repeat(64))), 201, "OK");
        assertCode(call("POST", "/admin/permissions", admin, permissionCreateBody("test:article:long")
                .replace("导出文章", "😀".repeat(65))), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/admin/permissions", admin, permissionCreateBody("test:article:blank")
                .replace("导出文章", "  ")), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/admin/permissions", admin, permissionCreateBody("test:article:description")
                .replace("}", ",\"description\":\"" + "字".repeat(256) + "\"}")), 400, "VALIDATION_ERROR");
        assertCode(call("GET", "/admin/permissions/01", admin, null), 400, "VALIDATION_ERROR");
        assertCode(call("GET", "/admin/permissions/9223372036854775808", admin, null), 400, "VALIDATION_ERROR");
        assertCode(call("GET", "/admin/permissions/9223372036854775807", admin, null), 404, "NOT_FOUND");
    }

    @Test
    void permissionListUsesLiteralFiltersStablePaginationAndCompleteShape() throws Exception {
        String admin = adminLogin("adminroot");
        String first = createPermission(admin, "test:article:first");
        String second = createPermission(admin, "test:article:second");
        String third = createPermission(admin, "test:article:third");
        jdbc.update(
                "UPDATE sys_permission SET permission_name='literal%_字', module='test_other', status=0 WHERE " +
                 "permission_id=?",
                second);
        MvcResult page = mvc.perform(req("GET", "/admin/permissions").header("Authorization", "Bearer " + admin)
                .param("keyword", " test:article: ").param("module", "test").param("status", "1")
                .param("page", "2").param("pageSize", "1")).andReturn();
        assertCode(page, 200, "OK");
        assertThat(data(page).get("page").asInt()).isEqualTo(2);
        assertThat(data(page).get("total").asString()).isEqualTo("2");
        assertThat(data(page).get("totalPages").asString()).isEqualTo("2");
        assertThat(data(page).get("records").get(0).get("permissionId").asString()).isEqualTo(third);
        assertThat(page.getResponse().getHeader("X-Revision")).isNull();
        for (String keyword : List.of("%", "_")) {
            MvcResult literal = mvc.perform(req("GET", "/admin/permissions").header("Authorization", "Bearer " + admin)
                    .param("keyword", keyword).param("module", "test_other").param("status", "0")).andReturn();
            assertCode(literal, 200, "OK");
            assertThat(data(literal).get("total").asString()).isEqualTo("1");
            assertThat(data(literal).get("records").get(0).get("permissionId").asString()).isEqualTo(second);
        }
        MvcResult empty = mvc.perform(req("GET", "/admin/permissions").header("Authorization", "Bearer " + admin)
                .param("keyword", "\\")).andReturn();
        assertThat(data(empty).get("records").isEmpty()).isTrue();
        assertThat(data(empty).get("totalPages").asString()).isEqualTo("0");
        MvcResult beyond = mvc.perform(req("GET", "/admin/permissions").header("Authorization", "Bearer " + admin)
                .param("module", "test").param("page", "100000")).andReturn();
        assertThat(data(beyond).get("page").asInt()).isEqualTo(100000);
        assertThat(data(beyond).get("records").isEmpty()).isTrue();
        assertThat(data(call("GET", "/admin/permissions/" + first, admin, null)).properties()).hasSize(8);
        for (Map.Entry<String, String> invalid : Map.of("page", "0", "pageSize", "101", "status", "2", "module", "Test",
                "keyword", " ").entrySet()) {
            assertCode(mvc.perform(req("GET", "/admin/permissions").header("Authorization", "Bearer " + admin)
                    .param(invalid.getKey(), invalid.getValue())).andReturn(), 400, "VALIDATION_ERROR");
        }
    }

    @Test
    void permissionPutIsCompleteImmutableAndVersionedWithoutUnnecessaryRevocation() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createPermission(admin, "test:article:export");
        String path = "/admin/permissions/" + id;
        String revision = permissionRevision(admin, id);
        assertCode(call("PUT", path, admin, permissionBody("导出", "test", 1)), 428, "PRECONDITION_REQUIRED");
        for (String invalid : List.of("*", "abc", revision + "," + revision, revision.replace("\"", ""))) {
            assertCode(adminPut(path, admin, invalid, permissionBody("导出", "test", 1)), 400, "VALIDATION_ERROR");
        }
        for (String field : List.of("\"permissionName\":\"导出\",", "\"module\":\"test\",", "\"description\":null,",
                ",\"status\":1")) {
            assertCode(adminPut(path, admin, revision, permissionBody("导出", "test", 1).replace(field, "")), 400,
                    "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"permissionCode\":\"test:article:changed\"", "\"permissionId\":\"1\"",
                "\"roleIds\":[]", "\"updateTime\":null")) {
            assertCode(
                    adminPut(path, admin, revision, permissionBody("导出", "test", 1).replace("}", "," + extra + "}")),
                    400, "VALIDATION_ERROR");
        }
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(1,?)", id);
        MvcResult updated = adminPut(path, admin, revision,
                permissionBody("  导出  ", " other_module ", 1).replace("null", "\"   \""));
        assertCode(updated, 200, "OK");
        assertThat(data(updated).get("permissionCode").asString()).isEqualTo("test:article:export");
        assertThat(data(updated).get("permissionName").asString()).isEqualTo("导出");
        assertThat(data(updated).get("module").asString()).isEqualTo("other_module");
        assertThat(data(updated).get("description").isNull()).isTrue();
        String current = updated.getResponse().getHeader("X-Revision");
        assertThat(current).matches("\"[0-9a-f]{64}\"").isNotEqualTo(revision);
        assertCode(call("GET", "/me", admin, null), 200, "OK");
        assertCode(adminPut(path, admin, revision, permissionBody("旧版本", "test", 1)), 412, "REVISION_CONFLICT");
        jdbc.update(
                "UPDATE sys_permission SET update_time=DATE_ADD(update_time,INTERVAL 1 SECOND) WHERE permission_id=?",
                id);
        assertThat(permissionRevision(admin, id)).isEqualTo(current);
        MvcResult unchanged = adminPut(path, admin, current, permissionBody("导出", "other_module", 1));
        assertCode(unchanged, 200, "OK");
        assertThat(unchanged.getResponse().getHeader("X-Revision")).isEqualTo(current);
        verify(sessions, times(0)).revokeAll(anyLong());
    }

    @Test
    void permissionStatusPreservesGrantsAndMenusAndRevokesEveryAffectedSession() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createPermission(admin, "test:article:export");
        String role = createRole(admin, "editor");
        String disabledRole = createRole(admin, "disabled_editor");
        String firstId = data(register("first_user")).get("userId").asString();
        String secondId = data(register("second_user")).get("userId").asString();
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(?,?),(?,?),(?,1)", role, id,
                disabledRole, id, role);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?),(?,?),(?,?)", firstId, role, firstId,
                disabledRole, secondId, disabledRole);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(?,100),(?,110)", role, role);
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=?", disabledRole);
        String first = login("first_user");
        String another = login("first_user");
        String second = login("second_user");
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", secondId);
        try {
            jdbc.update("UPDATE sys_menu SET permission_id=? WHERE menu_id=110", id);
            assertThat(authorization.capabilities(Long.parseLong(firstId)).permissionCodes()).contains(
                    "test:article:export");
            assertThat(authorization.menus(Long.parseLong(firstId))).hasSize(1);
            MvcResult disabled = adminPut("/admin/permissions/" + id, admin, permissionRevision(admin, id),
                    permissionBody("导出", "test", 0));
            assertCode(disabled, 200, "OK");
            for (String token : List.of(first, another, second)) {
                assertThat(StpUtil.getStpLogic().getLoginIdByToken(token)).isNull();
            }
            verify(sessions, times(1)).revokeAll(Long.parseLong(firstId));
            verify(sessions, times(1)).revokeAll(Long.parseLong(secondId));
            assertThat(authorization.capabilities(Long.parseLong(firstId)).permissionCodes()).doesNotContain(
                    "test:article:export");
            assertThat(authorization.menus(Long.parseLong(firstId))).isEmpty();
            assertThat(
                    jdbc.queryForObject("SELECT permission_id FROM sys_menu WHERE menu_id=110", Long.class)).isEqualTo(
                    Long.parseLong(id));
            assertThat(
                    jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id=?", Integer.class,
                            id)).isEqualTo(2);
            String whileDisabled = login("first_user");
            assertCode(adminPut("/admin/permissions/" + id, admin, disabled.getResponse().getHeader("X-Revision"),
                    permissionBody("导出", "test", 1)), 200, "OK");
            assertCode(call("GET", "/me", whileDisabled, null), 401, "UNAUTHENTICATED");
            assertCode(call("GET", "/me", first, null), 401, "UNAUTHENTICATED");
            assertThat(authorization.capabilities(Long.parseLong(firstId)).permissionCodes()).contains(
                    "test:article:export");
            assertThat(authorization.menus(Long.parseLong(firstId))).hasSize(1);
            assertCode(call("GET", "/me", login("first_user"), null), 200, "OK");
        } finally {
            jdbc.update("UPDATE sys_menu SET permission_id=10 WHERE menu_id=110");
        }
    }

    @Test
    void permissionDeletionRejectsFixedCodesAndAnyMenuReference() throws Exception {
        String admin = adminLogin("adminroot");
        for (String code : AuthorizationProvider.ADMIN_PERMISSIONS) {
            String id = jdbc.queryForObject("SELECT permission_id FROM sys_permission WHERE permission_code=?",
                    String.class, code);
            assertCode(roleDelete("/admin/permissions/" + id, admin, permissionRevision(admin, id)), 409,
                    "STATE_CONFLICT");
        }
        String id = createPermission(admin, "test:article:export");
        String revision = permissionRevision(admin, id);
        try {
            jdbc.update("UPDATE sys_menu SET permission_id=?, status=0 WHERE menu_id=110", id);
            assertCode(roleDelete("/admin/permissions/" + id, admin, revision), 409, "RESOURCE_IN_USE");
            assertThat(
                    jdbc.queryForObject("SELECT permission_id FROM sys_menu WHERE menu_id=110", Long.class)).isEqualTo(
                    Long.parseLong(id));
            assertThat(permissionRevision(admin, id)).isEqualTo(revision);
        } finally {
            jdbc.update("UPDATE sys_menu SET permission_id=10, status=1 WHERE menu_id=110");
        }
        assertCode(roleDelete("/admin/permissions/" + id, admin, revision), 200, "OK");
    }

    @Test
    void permissionDeleteCascadesRoleGrantsRevokesAllSessionsAndRollsBackOnRedisFailure() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createPermission(admin, "test:article:export");
        String role = createRole(admin, "editor");
        String user = data(register("permission_user")).get("userId").asString();
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(?,?)", role, id);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, role);
        String first = login("permission_user");
        String second = login("permission_user");
        String revision = permissionRevision(admin, id);
        String roleRevision = roleRevision(admin, role);
        String path = "/admin/permissions/" + id;
        assertCode(call("DELETE", path, admin, null), 428, "PRECONDITION_REQUIRED");
        assertCode(roleDelete(path, admin, "*"), 400, "VALIDATION_ERROR");
        assertCode(roleDelete(path, admin, "\"" + "0".repeat(64) + "\""), 412, "REVISION_CONFLICT");
        doThrow(new RedisConnectionFailureException("injected")).when(sessions).revokeAll(Long.parseLong(user));
        assertCode(roleDelete(path, admin, revision), 503, "SERVICE_UNAVAILABLE");
        assertThat(permissionRevision(admin, id)).isEqualTo(revision);
        assertThat(roleRevision(admin, role)).isEqualTo(roleRevision);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id=?", Integer.class,
                id)).isEqualTo(1);
        assertCode(call("GET", "/me", first, null), 200, "OK");
        reset(sessions);
        MvcResult deleted = roleDelete(path, admin, revision);
        assertCode(deleted, 200, "OK");
        assertThat(data(deleted).isNull()).isTrue();
        assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id=?", Integer.class,
                id)).isZero();
        assertThat(roleRevision(admin, role)).isNotEqualTo(roleRevision);
        for (String token : List.of(first, second)) {
            assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
        }
        assertCode(roleDelete(path, admin, revision), 404, "NOT_FOUND");
    }

    @Test
    void permissionStatusRollbackAndLastAdminUseFixedWhitelist() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createPermission(admin, "test:article:export");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(1,?)", id);
        long actor = Long.parseLong(data(call("GET", "/me", admin, null)).get("userId").asString());
        String revision = permissionRevision(admin, id);
        doThrow(new RedisConnectionFailureException("injected")).when(sessions).revokeAll(actor);
        assertCode(adminPut("/admin/permissions/" + id, admin, revision, permissionBody("不应保存", "other", 0)), 503,
                "SERVICE_UNAVAILABLE");
        assertThat(permissionRevision(admin, id)).isEqualTo(revision);
        assertThat(data(call("GET", "/admin/permissions/" + id, admin, null)).get("status").asInt()).isEqualTo(1);
        reset(sessions);
        for (String code : AuthorizationProvider.ADMIN_PERMISSIONS) {
            String fixedId = jdbc.queryForObject("SELECT permission_id FROM sys_permission WHERE permission_code=?",
                    String.class, code);
            String fixedRevision = permissionRevision(admin, fixedId);
            assertCode(adminPut("/admin/permissions/" + fixedId, admin, fixedRevision,
                    permissionBody("不可停用", "sys", 0)), 409, "LAST_ADMIN");
            assertThat(permissionRevision(admin, fixedId)).isEqualTo(fixedRevision);
        }
        assertCode(adminPut("/admin/permissions/" + id, admin, revision, permissionBody("导出", "test", 0)), 200, "OK");
        assertCode(call("GET", "/me", admin, null), 401, "UNAUTHENTICATED");
        admin = login("adminroot");
        assertCode(call("GET", "/admin/permissions", admin, null), 200, "OK");
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).doesNotContain("sys:log:query", "home:profile:query");
        assertCode(call("GET", "/admin/logs", admin, null), 404, "NOT_FOUND");
    }

    @Test
    void concurrentPermissionWritesShareRevisionAndPreserveAdminWithUserChanges() throws Exception {
        String first = adminLogin("first_admin");
        String second = adminLogin("second_admin");
        String id = createPermission(first, "test:article:export");
        String revision = permissionRevision(first, id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> writes = List.of(
                    () -> adminPut("/admin/permissions/" + id, first, revision, permissionBody("并发甲", "test", 1)),
                    () -> adminPut("/admin/permissions/" + id, second, revision, permissionBody("并发乙", "test", 1)));
            var results = executor.invokeAll(writes);
            assertThat(results.stream().map(result -> {
                try {
                    return result.get().getResponse().getStatus();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).sorted().toList()).containsExactly(200, 412);
            String user = data(call("GET", "/me", first, null)).get("userId").asString();
            String userRevision = adminRevision(second, user);
            String protectedRevision = permissionRevision(second, "80");
            List<Callable<MvcResult>> protectedWrites = List.of(
                    () -> adminPut("/admin/users/" + user, second, userRevision, userBody("叶", 0)),
                    () -> adminPut("/admin/permissions/80", second, protectedRevision,
                            permissionBody("不可停用", "sys", 0)));
            var protectedResults = executor.invokeAll(protectedWrites);
            assertCode(protectedResults.get(0).get(), 200, "OK");
            assertCode(protectedResults.get(1).get(), 409, "LAST_ADMIN");
        }
        assertCode(call("GET", "/admin/permissions", second, null), 200, "OK");
    }

    @Test
    void permissionWriteRechecksIdentityAndPermissionsAfterGuardWait() throws Exception {
        String admin = adminLogin("adminroot");
        long actor = Long.parseLong(data(call("GET", "/me", admin, null)).get("userId").asString());
        String id = createPermission(admin, "test:article:export");
        String revision = permissionRevision(admin, id);
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (String change : List.of("permission", "disabled", "token")) {
                CountDownLatch entered = new CountDownLatch(1);
                doAnswer(invocation -> {
                    entered.countDown();
                    return invocation.callRealMethod();
                }).when(AopTestUtils.<IPermissionService>getUltimateTargetObject(permissions))
                        .update(eq(Long.parseLong(id)), any(PermissionUpdateDTO.class), eq(revision));
                var pending = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_id=1 FOR UPDATE", Long.class);
                    var response = executor.submit(() -> adminPut("/admin/permissions/" + id, admin, revision,
                            permissionBody("不应保存", "test", 1)));
                    try {
                        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    if ("disabled".equals(change)) {
                        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", actor);
                    } else {
                        jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_id=1", actor);
                        if ("token".equals(change)) {
                            sessions.revokeAll(actor);
                        }
                    }
                    return response;
                });
                assertCode(pending.get(10, TimeUnit.SECONDS), "token".equals(change) ? 401 : 403,
                        "token".equals(change) ? "UNAUTHENTICATED" : "disabled".equals(
                                change) ? "ACCOUNT_DISABLED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT permission_name FROM sys_permission WHERE permission_id=?",
                        String.class, id)).isEqualTo("导出文章");
                if ("disabled".equals(change)) {
                    jdbc.update("UPDATE sys_user SET status=1 WHERE user_id=?", actor);
                } else {
                    jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", actor);
                }
            }
        }
    }

    @Test
    void menuRoutesRequireBothEffectivePermissionsAndCurrentIdentity() throws Exception {
        String user = data(register("menu_operator")).get("userId").asString();
        String token = login("menu_operator");
        List<String> methods = List.of("GET", "GET", "POST", "PUT", "DELETE");
        List<String> paths = List.of("/admin/menus", "/admin/menus/110", "/admin/menus", "/admin/menus/110",
                "/admin/menus/110");
        List<String> bodies = List.of("", "", menuCreateBody("Auth", null, "D", null),
                menuBody("Auth", null, "D", null), "");
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(9,'menu_operator','菜单管理','SELF')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,9)", user);
        for (int index = 0; index < methods.size(); index++) {
            assertCode(call(methods.get(index), paths.get(index), null, bodies.get(index)), 401, "UNAUTHENTICATED");
            assertCode(call(methods.get(index), paths.get(index), token, bodies.get(index)), 403, "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 9,permission_id FROM sys_permission " +
                 "WHERE permission_code LIKE 'sys:menu:%'");
        for (int index = 0; index < methods.size(); index++) {
            assertCode(call(methods.get(index), paths.get(index), token, bodies.get(index)), 403, "FORBIDDEN");
        }
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=9");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,1)");
        for (int index = 0; index < methods.size(); index++) {
            assertCode(call(methods.get(index), paths.get(index), token, bodies.get(index)), 403, "FORBIDDEN");
        }
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,100)");
        assertCode(call("GET", "/admin/menus", token, null), 200, "OK");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=100");
        assertCode(call("GET", "/admin/menus", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=100");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=9");
        assertCode(call("GET", "/admin/menus", token, null), 403, "FORBIDDEN");
        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", user);
        assertCode(call("GET", "/admin/menus", token, null), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void menuCreationDefaultsShapeUnicodeAndImmutableUniqueCodes() throws Exception {
        String admin = adminLogin("adminroot");
        String body = "{\"parentId\":null,\"menuCode\":\"test:Defaults\",\"menuName\":\"  " + "🌿".repeat(64)
                + "  \",\"menuType\":\"D\",\"routeName\":\"TestDefaults\",\"path\":\"/test/defaults\"," +
                 "\"component\":\"Layout\"}";
        MvcResult created = call("POST", "/admin/menus", admin, body);
        assertCode(created, 201, "OK");
        JsonNode value = data(created);
        String id = value.get("menuId").asString();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/menus/" + id);
        assertThat(created.getResponse().getHeader("X-Revision")).isNull();
        assertThat(value.properties()).hasSize(16);
        assertThat(value.get("menuName").asString()).isEqualTo("🌿".repeat(64));
        assertThat(value.get("parentId").isNull()).isTrue();
        assertThat(value.get("permissionId").isNull()).isTrue();
        assertThat(value.get("icon").isNull()).isTrue();
        assertThat(value.get("sortOrder").asInt()).isZero();
        assertThat(value.get("keepAlive").asInt()).isZero();
        assertThat(value.get("isVisible").asInt()).isEqualTo(1);
        assertThat(value.get("status").asInt()).isEqualTo(1);
        assertThat(value.get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=?", Integer.class, id)).isZero();
        assertCode(call("POST", "/admin/menus", admin, body), 409, "UNIQUE_CONFLICT");
        assertCode(call("POST", "/admin/menus", admin, body.replace("test:Defaults", "test:DuplicateRoute")
                .replace("/test/defaults", "/test/duplicate")), 409, "UNIQUE_CONFLICT");
        assertCode(call("POST", "/admin/menus", admin, body.replace("test:Defaults", "test:DuplicatePath")
                .replace("TestDefaults", "TestDuplicate")), 409, "UNIQUE_CONFLICT");
        assertCode(call("POST", "/admin/menus", admin, body.replace("test:Defaults", "test:defaults")
                .replace("TestDefaults", "Testdefaults").replace("/test/defaults", "/test/Defaults")), 201, "OK");
        assertCode(call("POST", "/admin/menus", admin, body.replace("🌿".repeat(64), "🌿".repeat(65))
                .replace("test:Defaults", "test:Long")), 400, "VALIDATION_ERROR");
        String button = "{\"parentId\":\"110\",\"menuCode\":\"test:Button\",\"menuName\":\"按钮\",\"menuType\":\"B\"," +
         "\"permissionId\":\"11\"}";
        MvcResult action = call("POST", "/admin/menus", admin, button);
        assertCode(action, 201, "OK");
        assertThat(data(action).get("isVisible").asInt()).isZero();
        assertThat(data(action).get("routeName").isNull()).isTrue();
        for (String field : List.of("sortOrder", "isVisible", "keepAlive", "status")) {
            assertCode(call("POST", "/admin/menus", admin, body.replace("}", ",\"" + field + "\":null}")), 400,
                    "VALIDATION_ERROR");
        }
    }

    @Test
    void menuInputRejectsUnknownFieldsWrongTypesInvalidIdsAndUnregisteredKeys() throws Exception {
        String admin = adminLogin("adminroot");
        String body = menuCreateBody("Strict", "100", "M", "10");
        for (String extra : List.of("\"menuId\":\"9\"", "\"roleIds\":[]", "\"createTime\":null", "\"updateTime\":null",
                "\"parentPresent\":true", "\"visiblePresent\":true", "\"completeCreate\":true",
                "\"menuName\":\"重复\"")) {
            assertCode(call("POST", "/admin/menus", admin, body.replace("}", "," + extra + "}")), 400,
                    "VALIDATION_ERROR");
        }
        Map<String, List<String>> invalid = Map.ofEntries(
                Map.entry("menuCode", List.of("null", "12", "\"菜单\"", "\"test.bad\"", "\"" + "a".repeat(65) + "\"")),
                Map.entry("menuName", List.of("null", "12", "\"  \"")),
                Map.entry("menuType", List.of("null", "1", "\"m\"", "\"X\"")),
                Map.entry("routeName",
                        List.of("null", "12", "\"1Name\"", "\"A-B\"", "\"中文\"", "\"" + "a".repeat(65) + "\"")),
                Map.entry("parentId",
                        List.of("100", "\"0\"", "\"01\"", "\"-1\"", "\"1.0\"", "\"9223372036854775808\"")),
                Map.entry("permissionId", List.of("10", "null", "\"01\"", "\"9223372036854775807\"")),
                Map.entry("component", List.of("null", "\"Unknown\"", "\"https://remote.test/a.js\"", "\"alert(1)\"")),
                Map.entry("icon", List.of("12", "\"unknown\"", "\"<svg>\"")),
                Map.entry("sortOrder", List.of("\"1\"", "true", "1.5", "2147483648")),
                Map.entry("status", List.of("\"1\"", "true", "2", "-1", "1.1")),
                Map.entry("isVisible", List.of("2", "true", "\"1\"")),
                Map.entry("keepAlive", List.of("2", "true", "\"1\"")));
        Map<String, Object> fields = json.readValue(body, new TypeReference<>() {
        });
        for (var entry : invalid.entrySet()) {
            for (String value : entry.getValue()) {
                Map<String, Object> changed = new LinkedHashMap<>(fields);
                changed.remove(entry.getKey());
                String replaced = json.writeValueAsString(changed)
                        .replace("}", ",\"" + entry.getKey() + "\":" + value + "}");
                assertCode(call("POST", "/admin/menus", admin, replaced), 400, "VALIDATION_ERROR");
            }
        }
        for (String key : List.of("parentId", "menuCode", "menuName", "menuType", "routeName", "component", "path",
                "permissionId")) {
            Map<String, Object> missing = new LinkedHashMap<>(fields);
            missing.remove(key);
            assertCode(call("POST", "/admin/menus", admin, json.writeValueAsString(missing)), 400, "VALIDATION_ERROR");
        }
        for (String path : List.of("01", "0", "-1", "1.0", "9223372036854775808")) {
            for (String method : List.of("GET", "PUT", "DELETE")) {
                assertCode(call(method, "/admin/menus/" + path, admin,
                        "PUT".equals(method) ? menuBody("Strict", "100", "M", "10") : null), 400, "VALIDATION_ERROR");
            }
        }
        assertCode(call("GET", "/admin/menus/9223372036854775807", admin, null), 404, "NOT_FOUND");
    }

    @Test
    void menuPathsAndTypeShapesAreValidatedBeforePersistence() throws Exception {
        String admin = adminLogin("adminroot");
        Map<String, Object> fields = json.readValue(menuCreateBody("Path", null, "M", "10"), new TypeReference<>() {
        });
        for (String path : List.of("https://remote.test/a", "//remote.test/a", "relative", "/a/../b", "/a..b", "/a\\b",
                "/a?b=1", "/a#b", "/a:b", "/中文", "/a b", "/a\nb", "/%2e%2e/a", "/%5c/a", "/%3f/a", "/%ff",
                "/" + "a".repeat(255))) {
            fields.put("path", path);
            assertCode(call("POST", "/admin/menus", admin, json.writeValueAsString(fields)), 400, "VALIDATION_ERROR");
        }
        for (String type : List.of("D", "M", "B")) {
            Map<String, Object> shape = json.readValue(
                    menuCreateBody("Shape" + type, "B".equals(type) ? "110" : null, type,
                            "D".equals(type) ? null : "10"), new TypeReference<>() {
                    });
            if ("B".equals(type)) {
                for (String field : List.of("routeName", "path", "component", "isVisible", "keepAlive")) {
                    Map<String, Object> wrong = new LinkedHashMap<>(shape);
                    Object wrongValue = switch (field) {
                        case "isVisible", "keepAlive" -> 1;
                        case "routeName" -> "TestShapeB";
                        case "path" -> "/test/shape";
                        default -> "Layout";
                    };
                    wrong.put(field, wrongValue);
                    assertCode(call("POST", "/admin/menus", admin, json.writeValueAsString(wrong)), 400,
                            "VALIDATION_ERROR");
                }
            } else if ("D".equals(type)) {
                shape.put("permissionId", "10");
                assertCode(call("POST", "/admin/menus", admin, json.writeValueAsString(shape)), 400,
                        "VALIDATION_ERROR");
                shape.put("permissionId", null);
                shape.put("keepAlive", 1);
                assertCode(call("POST", "/admin/menus", admin, json.writeValueAsString(shape)), 400,
                        "VALIDATION_ERROR");
            }
        }
        for (String type : List.of("D", "M", "B")) {
            for (String parent : List.of("9223372036854775807", "110", "1011")) {
                if ("B".equals(type) && "110".equals(parent)) {
                    continue;
                }
                assertCode(call("POST", "/admin/menus", admin,
                                menuCreateBody("Parent" + type, parent, type, "D".equals(type) ? null : "10")), 400,
                        "VALIDATION_ERROR");
            }
        }
        assertCode(call("POST", "/admin/menus", admin, menuCreateBody("RootB", null, "B", "10")), 400,
                "VALIDATION_ERROR");
        assertCode(call("POST", "/admin/menus", admin, menuCreateBody("DirectoryB", "100", "B", "10")), 400,
                "VALIDATION_ERROR");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=10");
        assertCode(call("POST", "/admin/menus", admin, menuCreateBody("DisabledGate", "100", "M", "10")), 201, "OK");
    }

    @Test
    void menuFullTreeKeepsAllStatesEmptyDirectoriesAndStableOrder() throws Exception {
        String admin = adminLogin("adminroot");
        String root = createMenu(admin, "Tree", null, "D", null);
        String first = createMenu(admin, "TreeFirst", root, "M", "10");
        String second = createMenu(admin, "TreeSecond", root, "M", "20");
        String button = createMenu(admin, "TreeButton", first, "B", "11");
        String empty = createMenu(admin, "TreeEmpty", root, "D", null);
        jdbc.update("UPDATE sys_menu SET is_visible=0,status=0,sort_order=-10 WHERE menu_id=?", second);
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=?", root);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=20");
        MvcResult tree = call("GET", "/admin/menus", admin, null);
        assertCode(tree, 200, "OK");
        assertThat(tree.getResponse().getHeader("X-Revision")).isNull();
        JsonNode branch = findMenu(data(tree), root);
        assertThat(branch).isNotNull();
        assertThat(branch.get("children").get(0).get("menuId").asString()).isEqualTo(second);
        assertThat(branch.get("children").get(1).get("menuId").asString()).isEqualTo(first);
        assertThat(branch.get("children").get(2).get("menuId").asString()).isEqualTo(empty);
        assertThat(findMenu(branch.get("children"), button).get("children").isEmpty()).isTrue();
        assertThat(findMenu(branch.get("children"), second).get("permissionCode").asString()).isEqualTo(
                "blog:category:query");
        assertThat(branch.properties()).hasSize(18);
        assertThat(findMenu(data(call("GET", "/me/menus", admin, null)), root)).isNull();
        MvcResult detail = call("GET", "/admin/menus/" + root, admin, null);
        assertThat(data(detail).properties()).hasSize(16);
        assertThat(detail.getResponse().getHeader("X-Revision")).matches("\"[0-9a-f]{64}\"");
    }

    @Test
    void menuPutRequiresCompleteBodyAndVersionIncludingNoOpAndNullClearing() throws Exception {
        String admin = adminLogin("adminroot");
        String id = createMenu(admin, "Replace", "100", "M", "10");
        String path = "/admin/menus/" + id;
        String revision = menuRevision(admin, id);
        String body = menuBody("Replace", "100", "M", "10");
        assertCode(call("PUT", path, admin, body), 428, "PRECONDITION_REQUIRED");
        assertCode(call("DELETE", path, admin, null), 428, "PRECONDITION_REQUIRED");
        for (String invalid : List.of("*", "abc", revision + "," + revision, revision.replace("\"", ""))) {
            assertCode(adminPut(path, admin, invalid, body), 400, "VALIDATION_ERROR");
            assertCode(roleDelete(path, admin, invalid), 400, "VALIDATION_ERROR");
        }
        Map<String, Object> fields = json.readValue(body, new TypeReference<>() {
        });
        for (String key : fields.keySet()) {
            Map<String, Object> incomplete = new LinkedHashMap<>(fields);
            incomplete.remove(key);
            assertCode(adminPut(path, admin, revision, json.writeValueAsString(incomplete)), 400, "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"menuCode\":\"test:Other\"", "\"menuId\":\"9\"", "\"roleIds\":[]",
                "\"present\":[\"icon\"]",
                "\"completeReplace\":true", "\"createTime\":null", "\"updateTime\":null")) {
            assertCode(adminPut(path, admin, revision, body.replace("}", "," + extra + "}")), 400, "VALIDATION_ERROR");
        }
        MvcResult same = adminPut(path, admin, revision, body);
        assertCode(same, 200, "OK");
        assertThat(same.getResponse().getHeader("X-Revision")).isEqualTo(revision);
        assertCode(adminPut(path, admin, revision, body.replace("TestReplace", "Content")), 409, "UNIQUE_CONFLICT");
        assertCode(adminPut(path, admin, revision, body.replace("/test/Replace", "/content")), 409, "UNIQUE_CONFLICT");
        assertThat(menuRevision(admin, id)).isEqualTo(revision);
        jdbc.update("UPDATE sys_menu SET update_time=DATE_ADD(update_time,INTERVAL 1 SECOND) WHERE menu_id=?", id);
        assertThat(menuRevision(admin, id)).isEqualTo(revision);
        MvcResult changed = adminPut(path, admin, revision, menuBody("Replace", "110", "B", "11"));
        assertCode(changed, 200, "OK");
        assertThat(data(changed).get("menuCode").asString()).isEqualTo("test:Replace");
        for (String key : List.of("routeName", "path", "component", "icon")) {
            assertThat(data(changed).get(key).isNull()).isTrue();
        }
        String current = changed.getResponse().getHeader("X-Revision");
        assertThat(current).isNotEqualTo(revision);
        assertCode(adminPut(path, admin, revision, menuBody("Replace", "110", "B", "11")), 412, "REVISION_CONFLICT");
        assertCode(roleDelete(path, admin, revision), 412, "REVISION_CONFLICT");
        MvcResult directory = adminPut(path, admin, current, menuBody("Replace", null, "D", null));
        assertCode(directory, 200, "OK");
        assertThat(data(directory).get("parentId").isNull()).isTrue();
        assertThat(data(directory).get("permissionId").isNull()).isTrue();
    }

    @Test
    void menuMovesAndTypeChangesValidateEntireDescendantBranch() throws Exception {
        String admin = adminLogin("adminroot");
        String root = createMenu(admin, "Ancestor", null, "D", null);
        String directory = createMenu(admin, "Nested", root, "D", null);
        String page = createMenu(admin, "NestedPage", directory, "M", "10");
        String button = createMenu(admin, "NestedButton", page, "B", "11");
        String revision = menuRevision(admin, root);
        for (String parent : List.of(root, directory, page, button)) {
            assertCode(adminPut("/admin/menus/" + root, admin, revision, menuBody("Ancestor", parent, "D", null)), 400,
                    "VALIDATION_ERROR");
        }
        assertCode(adminPut("/admin/menus/" + root, admin, revision, menuBody("Ancestor", null, "M", "10")), 400,
                "VALIDATION_ERROR");
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page),
                menuBody("NestedPage", directory, "D", null)), 400, "VALIDATION_ERROR");
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page),
                menuBody("NestedPage", "110", "B", "11")), 400, "VALIDATION_ERROR");
        assertThat(menuRevision(admin, root)).isEqualTo(revision);
        assertCode(roleDelete("/admin/menus/" + root, admin, revision), 409, "RESOURCE_IN_USE");
        assertCode(roleDelete("/admin/menus/" + page, admin, menuRevision(admin, page)), 409, "RESOURCE_IN_USE");
        assertCode(roleDelete("/admin/menus/" + button, admin, menuRevision(admin, button)), 200, "OK");
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page),
                menuBody("NestedPage", directory, "D", null)), 200, "OK");
    }

    @Test
    void menuStateVisibilityGatesMovesAndDeletePreserveIndependentGrantsAndSessions() throws Exception {
        String admin = adminLogin("adminroot");
        String role = createRole(admin, "menu_viewer");
        String user = data(register("menu_viewer")).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, role);
        String root = createMenu(admin, "GrantedRoot", null, "D", null);
        String other = createMenu(admin, "OtherRoot", null, "D", null);
        String page = createMenu(admin, "GrantedPage", root, "M", "10");
        String button = createMenu(admin, "GrantedButton", page, "B", "11");
        assertCode(adminPut("/admin/roles/" + role + "/grants", admin, roleRevision(admin, role),
                grantBody(List.of(1L, 10L, 11L), List.of(Long.parseLong(button)))), 200, "OK");
        String token = login("menu_viewer");
        String second = login("menu_viewer");
        String roleVersion = roleRevision(admin, role);
        String grants = data(call("GET", "/admin/roles/" + role, admin, null)).get("permissionIds").toString();
        Map<String, Object> fields = json.readValue(menuBody("GrantedPage", root, "M", "10"), new TypeReference<>() {
        });
        fields.put("isVisible", 0);
        fields.put("keepAlive", 1);
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page), json.writeValueAsString(fields)),
                200, "OK");
        assertThat(findMenu(data(call("GET", "/me/menus", token, null)), page).get("isVisible").asInt()).isZero();
        assertThat(findMenu(data(call("GET", "/me/menus", token, null)), button)).isNotNull();
        fields.put("status", 0);
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page), json.writeValueAsString(fields)),
                200, "OK");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("permissionCodes").toString()).contains(
                "blog:article:query");
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page),
                menuBody("GrantedPage", root, "M", "20")), 200, "OK");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/admin/roles/" + role, admin, null)).get("permissionIds").toString()).isEqualTo(
                grants);
        assertCode(adminPut("/admin/menus/" + page, admin, menuRevision(admin, page),
                menuBody("GrantedPage", other, "M", "10")), 200, "OK");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(roleRevision(admin, role)).isEqualTo(roleVersion);
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=? AND menu_id=?", Integer.class,
                        role, other)).isZero();
        assertCode(adminPut("/admin/roles/" + role + "/grants", admin, roleVersion,
                grantBody(List.of(1L, 10L, 11L), List.of(Long.parseLong(button)))), 200, "OK");
        token = login("menu_viewer");
        second = login("menu_viewer");
        assertThat(findMenu(data(call("GET", "/me/menus", token, null)), page)).isNotNull();
        String beforeDelete = roleRevision(admin, role);
        doThrow(new RedisConnectionFailureException("must not revoke menu sessions")).when(sessions)
                .revokeAll(Long.parseLong(user));
        MvcResult deleted = roleDelete("/admin/menus/" + button, admin, menuRevision(admin, button));
        assertCode(deleted, 200, "OK");
        assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(deleted).isNull()).isTrue();
        assertThat(roleRevision(admin, role)).isNotEqualTo(beforeDelete);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=?", Integer.class,
                button)).isZero();
        assertThat(data(call("GET", "/admin/roles/" + role, admin, null)).get("permissionIds").toString()).isEqualTo(
                grants);
        assertCode(call("GET", "/me", token, null), 200, "OK");
        assertCode(call("GET", "/me", second, null), 200, "OK");
        assertCode(roleDelete("/admin/menus/" + button, admin, "\"" + "0".repeat(64) + "\""), 404, "NOT_FOUND");
    }

    @Test
    void concurrentMenuMovesCannotCreateCycleAndSharedRevisionRejectsLostUpdate() throws Exception {
        String admin = adminLogin("adminroot");
        String left = createMenu(admin, "Left", null, "D", null);
        String right = createMenu(admin, "Right", null, "D", null);
        String firstRevision = menuRevision(admin, left);
        String secondRevision = menuRevision(admin, right);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> moves = List.of(
                    () -> adminPut("/admin/menus/" + left, admin, firstRevision, menuBody("Left", right, "D", null)),
                    () -> adminPut("/admin/menus/" + right, admin, secondRevision, menuBody("Right", left, "D", null)));
            var results = executor.invokeAll(moves);
            assertThat(List.of(results.get(0).get().getResponse().getStatus(),
                    results.get(1).get().getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 400);
            String revision = menuRevision(admin, left);
            String parent = data(call("GET", "/admin/menus/" + left, admin, null)).get("parentId")
                    .isNull() ? null : right;
            List<Callable<MvcResult>> writes = List.of(
                    () -> adminPut("/admin/menus/" + left, admin, revision,
                            menuBody("Left", parent, "D", null).replace("菜单", "甲")),
                    () -> adminPut("/admin/menus/" + left, admin, revision,
                            menuBody("Left", parent, "D", null).replace("菜单", "乙")));
            var changed = executor.invokeAll(writes);
            assertThat(List.of(changed.get(0).get().getResponse().getStatus(),
                    changed.get(1).get().getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 412);
        }
    }

    @Test
    void menuWriteRechecksIdentityPermissionAndTokenAfterGuardWaitAndRedisFailureRollsBack() throws Exception {
        String admin = adminLogin("adminroot");
        long actor = Long.parseLong(data(call("GET", "/me", admin, null)).get("userId").asString());
        String id = createMenu(admin, "Guarded", null, "D", null);
        String revision = menuRevision(admin, id);
        doThrow(new RedisConnectionFailureException("injected active check failure")).when(sessions)
                .requireActive(admin, actor);
        assertCode(adminPut("/admin/menus/" + id, admin, revision,
                menuBody("Guarded", null, "D", null).replace("菜单", "不应保存")), 503, "SERVICE_UNAVAILABLE");
        reset(sessions);
        assertThat(menuRevision(admin, id)).isEqualTo(revision);
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (String change : List.of("permission", "disabled", "token")) {
                CountDownLatch entered = new CountDownLatch(1);
                doAnswer(invocation -> {
                    entered.countDown();
                    return invocation.callRealMethod();
                }).when(AopTestUtils.<IMenuService>getUltimateTargetObject(menus))
                        .update(eq(Long.parseLong(id)), any(MenuUpdateDTO.class), eq(revision));
                var pending = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_id=1 FOR UPDATE", Long.class);
                    var response = executor.submit(() -> adminPut("/admin/menus/" + id, admin, revision,
                            menuBody("Guarded", null, "D", null).replace("菜单", "不应保存")));
                    try {
                        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    if ("disabled".equals(change)) {
                        jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", actor);
                    } else {
                        jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_id=1", actor);
                        if ("token".equals(change)) {
                            sessions.revokeAll(actor);
                        }
                    }
                    return response;
                });
                assertCode(pending.get(10, TimeUnit.SECONDS), "token".equals(change) ? 401 : 403,
                        "token".equals(change) ? "UNAUTHENTICATED" : "disabled".equals(
                                change) ? "ACCOUNT_DISABLED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT menu_name FROM sys_menu WHERE menu_id=?", String.class,
                        id)).isEqualTo("菜单");
                if ("disabled".equals(change)) {
                    jdbc.update("UPDATE sys_user SET status=1 WHERE user_id=?", actor);
                } else {
                    jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", actor);
                }
            }
        }
    }

    private String menuBody(String key, String parent, String type, String permission) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("parentId", parent);
        fields.put("menuName", "菜单");
        fields.put("menuType", type);
        fields.put("routeName", "B".equals(type) ? null : "Test" + key);
        fields.put("path", "B".equals(type) ? null : "/test/" + key);
        String component;
        if ("B".equals(type)) {
            component = null;
        } else if ("D".equals(type)) {
            component = "Layout";
        } else {
            component = "content/article/index";
        }
        fields.put("component", component);
        fields.put("icon", null);
        fields.put("permissionId", permission);
        fields.put("sortOrder", 0);
        fields.put("isVisible", "B".equals(type) ? 0 : 1);
        fields.put("keepAlive", 0);
        fields.put("status", 1);
        return json.writeValueAsString(fields);
    }

    private String menuCreateBody(String key, String parent, String type, String permission) {
        return menuBody(key, parent, type, permission).replace("{", "{\"menuCode\":\"test:" + key + "\",");
    }

    private String createMenu(String token, String key, String parent, String type,
            String permission) throws Exception {
        MvcResult result = call("POST", "/admin/menus", token, menuCreateBody(key, parent, type, permission));
        assertCode(result, 201, "OK");
        return data(result).get("menuId").asString();
    }

    private String menuRevision(String token, String id) throws Exception {
        MvcResult result = call("GET", "/admin/menus/" + id, token, null);
        assertCode(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    private JsonNode findMenu(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (node.get("menuId").asString().equals(id)) {
                return node;
            }
            JsonNode found = findMenu(node.get("children"), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String permissionCreateBody(String code) {
        return "{\"permissionCode\":\"" + code + "\",\"permissionName\":\"导出文章\",\"module\":\"test\"}";
    }

    private String permissionBody(String name, String module, int status) {
        return "{\"permissionName\":\"" + name + "\",\"module\":\"" + module + "\",\"description\":null,\"status\":" + status + "}";
    }

    private String createPermission(String token, String code) throws Exception {
        MvcResult result = call("POST", "/admin/permissions", token, permissionCreateBody(code));
        assertCode(result, 201, "OK");
        return data(result).get("permissionId").asString();
    }

    private String permissionRevision(String token, String id) throws Exception {
        MvcResult result = call("GET", "/admin/permissions/" + id, token, null);
        assertCode(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    private String roleCreateBody(String code) {
        return "{\"roleCode\":\"" + code + "\",\"roleName\":\"内容编辑\"}";
    }

    private String roleBody(String name, String scope, int status) {
        return "{\"roleName\":\"" + name + "\",\"description\":null,\"dataScope\":\"" + scope + "\",\"status\":" + status + "}";
    }

    private String grantBody(List<Long> permissions, List<Long> menus) {
        return json.writeValueAsString(Map.of("permissionIds", permissions, "menuIds", menus));
    }

    private String createRole(String token, String code) throws Exception {
        MvcResult created = call("POST", "/admin/roles", token, roleCreateBody(code));
        assertCode(created, 201, "OK");
        return data(created).get("roleId").asString();
    }

    private String roleRevision(String token, String id) throws Exception {
        MvcResult result = call("GET", "/admin/roles/" + id, token, null);
        assertCode(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    private MvcResult roleDelete(String path, String token, String revision) throws Exception {
        return mvc.perform(req("DELETE", path).header("Authorization", "Bearer " + token)
                .header("If-Revision", revision)).andReturn();
    }

    private String adminLogin(String username) throws Exception {
        String id = data(register(username)).get("userId").asString();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", id);
        return login(username);
    }

    private String createUserBody(String username) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"叶\"}";
    }

    private String userBody(String nickname, int status) {
        return "{\"nickname\":\"" + nickname + "\",\"email\":null,\"avatar\":null,\"intro\":null,\"status\":" + status + "}";
    }

    private String adminRevision(String token, String id) throws Exception {
        MvcResult result = call("GET", "/admin/users/" + id, token, null);
        assertCode(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    private MvcResult adminPut(String path, String token, String revision, String body) throws Exception {
        return mvc.perform(req("PUT", path).header("Authorization", "Bearer " + token)
                .header("If-Revision", revision).contentType("application/json").content(body)).andReturn();
    }

    private MvcResult register(String username) throws Exception {
        return call("POST", "/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"叶\"}");
    }

    private String login(String username) throws Exception {
        MvcResult result = call("POST", "/auth/login", null, loginBody(username, PASSWORD));
        assertCode(result, 200, "OK");
        return data(result).get("accessToken").asString();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult call(String method, String path, String token, String body) throws Exception {
        var request = req(method, path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType("application/json").content(body);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult patch(String token, String revision, String body) throws Exception {
        return mvc.perform(req("PATCH", "/me/profile").header("Authorization", "Bearer " + token)
                .header("If-Revision", revision).contentType("application/json").content(body)).andReturn();
    }

    private MockHttpServletRequestBuilder req(String method, String path) {
        return request(HttpMethod.valueOf(method), "/api/v1" + path).contextPath("/api/v1")
                .servletPath(path.split("\\?", 2)[0]);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private void assertCode(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("code")
                .asString()).isEqualTo(code);
    }

    @Test
    void identityModelsRejectUndeclaredRepeatedAndBlankQueryInputs() throws Exception {
        String token = adminLogin("model_queries");
        String id = data(call("GET", "/me", token, null)).get("userId").asString();
        for (String path : List.of("/auth/oauth/providers", "/me", "/me/capabilities", "/me/menus",
                "/me/oauth-bindings",
                "/admin/users", "/admin/users/" + id, "/admin/roles", "/admin/roles/1",
                "/admin/permissions", "/admin/permissions/1", "/admin/menus", "/admin/menus/100")) {
            assertCode(call("GET", path + "?userId=1", token, null), 400, "VALIDATION_ERROR");
            assertCode(call("GET", path, token, "{}"), 400, "VALIDATION_ERROR");
        }
        for (String path : List.of("/admin/users", "/admin/roles", "/admin/permissions")) {
            for (String query : List.of("page=", "page=1&page=2", "pageSize=10&pageSize=20", "status=")) {
                assertCode(call("GET", path + "?" + query, token, null), 400, "VALIDATION_ERROR");
            }
            assertCode(mvc.perform(req("GET", path).header("Authorization", "Bearer " + token)
                    .param("keyword", " \t ")).andReturn(), 400, "VALIDATION_ERROR");
            assertCode(call("GET", path + "?page=1&pageSize=5&keyword=model&status=1", token, null), 200, "OK");
        }
        assertCode(call("GET", "/admin/users?roleId=1", token, null), 200, "OK");
        assertCode(call("GET", "/admin/permissions?module=sys", token, null), 200, "OK");
    }

    @Test
    void rejectedIdentityInputsDoNotCreateUsersLogoutOrUnbind() throws Exception {
        String token = adminLogin("model_writes");
        assertCode(call("POST", "/auth/register?status=0", null, createUserBody("must_not_exist")), 400,
                "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='must_not_exist'",
                Integer.class)).isZero();
        assertCode(call("POST", "/auth/login?userId=1", null, loginBody("model_writes", PASSWORD)), 400,
                "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/logout", token, "{}"), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/logout?userId=1", token, null), 400, "VALIDATION_ERROR");
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, "{}"), 400, "VALIDATION_ERROR");
        assertCode(call("GET", "/me", token, null), 200, "OK");
        assertCode(call("POST", "/auth/logout", token, null), 200, "OK");
        assertCode(call("GET", "/me", token, null), 401, "UNAUTHENTICATED");
    }

    @Test
    void invalidCallbackParametersDoNotConsumeTheOAuthFlow() throws Exception {
        OAuthFlow flow = start("LOGIN", null, null);
        for (String extra : List.of("userId", "state", "code")) {
            assertCode(mvc.perform(req("GET", "/auth/oauth/GITHUB/callback").param("state", flow.state())
                            .param("code", "model_identity").param(extra, "unexpected")
                            .cookie(new Cookie("__Host-dl-oauth-browser", flow.browser()))).andReturn(), 400,
                    "VALIDATION_ERROR");
            assertThat(store.containsKey(flow.state())).isTrue();
        }
        String ticket = callbackTicket(flow, "model_identity");
        assertCode(exchange(ticket, flow.browser(), null), 200, "OK");
    }

    @Test
    void roleAndPermissionWritesRecheckOriginalTokenAfterTheirTargetLock() throws Exception {
        String token = adminLogin("model_target_lock");
        long actor = Long.parseLong(data(call("GET", "/me", token, null)).get("userId").asString());
        for (String kind : List.of("roles", "permissions")) {
            String id = kind.equals("roles") ? createRole(token, "model_lock") : createPermission(token,
                    "test:model:lock");
            String path = "/admin/" + kind + "/" + id;
            String version = call("GET", path, token, null).getResponse().getHeader("X-Revision");
            String table = kind.equals("roles") ? "sys_role" : "sys_permission";
            String key = kind.equals("roles") ? "role_id" : "permission_id";
            for (String operation : kind.equals("roles") ? List.of("update", "delete", "grant",
                    "grantPermission") : List.of("update", "delete")) {
                String active = login("model_target_lock");
                boolean grant = operation.startsWith("grant");
                String body;
                if (grant) {
                    body = grantBody(operation.equals("grantPermission") ? List.of(1L) : List.of(), List.of());
                } else if (kind.equals("roles")) {
                    body = roleBody("不能保存", "SELF", 1);
                } else {
                    body = permissionBody("不能保存", "test", 1);
                }
                String lockedTable = operation.equals("grantPermission") ? "sys_permission" : table;
                String lockedKey = operation.equals("grantPermission") ? "permission_id" : key;
                String lockedId = operation.equals("grantPermission") ? "1" : id;
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var pending = new TransactionTemplate(transactionManager).execute(state -> {
                        jdbc.queryForList(
                                "SELECT " + lockedKey + " FROM " + lockedTable + " WHERE " + lockedKey + "=? FOR " +
                                 "UPDATE",
                                lockedId);
                        var writer = executor.submit(() -> operation.equals("delete")
                                ? roleDelete(path, active, version)
                                : adminPut(path + (grant ? "/grants" : ""), active, version, body));
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        boolean waiting = false;
                        while (System.nanoTime() < deadline) {
                            if (jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN " +
                                     "performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID" +
                                      " WHERE l.OBJECT_NAME=?",
                                    Integer.class, lockedTable) > 0) {
                                waiting = true;
                                break;
                            }
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }
                        }
                        assertThat(waiting).as("实际等到目标行锁后再撤销Token").isTrue();
                        sessions.revokeAll(actor);
                        return writer;
                    });
                    assertCode(pending.get(10, TimeUnit.SECONDS), 401, "UNAUTHENTICATED");
                }
                token = login("model_target_lock");
                assertThat(call("GET", path, token, null).getResponse().getHeader("X-Revision")).isEqualTo(version);
            }
        }
        awaitAudits();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE result=0 AND error_message='UNAUTHENTICATED'",
                Integer.class)).isEqualTo(6);
    }

    @Test
    void oauthCallbacksShareBudgetAcrossProvidersAndRejectBeforeStateConsumption() throws Exception {
        OAuthFlow flow = start("LOGIN", null, null);
        String raw = store.read("state", flow.state());
        for (int index = 0; index < 30; index++) {
            String source = index % 2 == 0 ? "GITHUB" : "GITEE";
            assertCode(mvc.perform(req("GET", "/auth/oauth/" + source + "/callback")
                    .param("state", TokenUtils.random()).param("code", "verified")
                    .cookie(new Cookie("__Host-dl-oauth-browser", flow.browser()))).andReturn(), 400, "OAUTH_STATE_INVALID");
        }
        MvcResult rejected = callback(flow, "verified");
        assertCode(rejected, 429, "RATE_LIMITED");
        assertThat(Integer.parseInt(rejected.getResponse().getHeader("Retry-After"))).isPositive();
        assertThat(store.read("state", flow.state())).isEqualTo(raw);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isZero();
        redis.delete(redis.keys("dl:test:rate:interface:oauth-callback:*"));
        assertThat(callback(flow, "verified").getResponse().getStatus()).isEqualTo(303);
    }

    private OAuthFlow start(String operation, String token, String browser) throws Exception {
        var request = req("POST", "/auth/oauth/GITHUB/authorize").header("Origin", ORIGIN)
                .contentType("application/json").content("{\"operation\":\"" + operation + "\"}");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (browser != null) {
            request.cookie(new Cookie("__Host-dl-oauth-browser", browser));
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertCode(result, 200, "OK");
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=600");
        String url = data(result).get("authorizationUrl").asString();
        String state = URLDecoder.decode(url.substring(url.indexOf("state=") + 6).split("&")[0],
                StandardCharsets.UTF_8);
        return new OAuthFlow(state, cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';')));
    }

    private MvcResult callback(OAuthFlow flow, String identity) throws Exception {
        return mvc.perform(
                req("GET", "/auth/oauth/GITHUB/callback").param("state", flow.state()).param("code", identity)
                        .cookie(new Cookie("__Host-dl-oauth-browser", flow.browser()))).andReturn();
    }

    private String callbackTicket(OAuthFlow flow, String identity) throws Exception {
        MvcResult callback = callback(flow, identity);
        assertThat(callback.getResponse().getStatus()).isEqualTo(303);
        String location = callback.getResponse().getHeader("Location");
        assertThat(location).startsWith(ORIGIN + "/oauth/complete#ticket=");
        return location.substring(location.indexOf("#ticket=") + 8);
    }

    private MvcResult exchange(String ticket, String browser, String token) throws Exception {
        var request = req("POST", "/auth/oauth/exchange").header("Origin", ORIGIN)
                .cookie(new Cookie("__Host-dl-oauth-browser", browser)).contentType("application/json")
                .content("{\"ticket\":\"" + ticket + "\"}");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn();
    }

    record OAuthFlow(String state, String browser) {

    }

    interface ArticleTestMapper extends BaseMapper<BlogArticle> {

    }

    interface FileTestMapper extends BaseMapper<BlogFile> {

    }

    /**
     * 只有平台网络返回身份用替身，state 检查和平台 URL 仍走实际 JustAuth。
     */
    @TestConfiguration
    static class Boundaries {

        @Bean
        @Primary
        OAuthProviderClient platform(JustAuthClient actual, RedisOAuthStore cache) {
            return new OAuthProviderClient() {
                @Override
                public List<Provider> providers() {
                    return actual.providers();
                }

                @Override
                public Set<String> enabledSources() {
                    return actual.enabledSources();
                }

                @Override
                public void requireEnabled(String source) {
                    actual.requireEnabled(source);
                }

                @Override
                public String authorize(String source, String state) {
                    return actual.authorize(source, state);
                }

                @Override
                public Identity verify(String source, String state, String code) {
                    AuthChecker.checkState(state, AuthDefaultSource.valueOf(source), cache);
                    return new Identity(code, "😀".repeat(70), "javascript:invalid");
                }
            };
        }

        @Bean
        TestAdminController testAdminController() {
            return new TestAdminController();
        }
    }

    /**
     * 这个入口只放测试 classpath，用来检查真实 Sa-Token 过滤和注解能否一起生效。
     */
    @RestController
    static class TestAdminController {

        @GetMapping("/admin/test/rate-user")
        @SaCheckPermission("sys:user:query")
        @RateLimit(value = "test.user", limit = 2, dimension = RateLimit.Dimension.USER)
        public Result<Void> rateUser() {
            return Result.ok(null);
        }

        @GetMapping("/admin/test/rate-token")
        @SaCheckPermission("sys:user:query")
        @RateLimit(value = "test.token", limit = 2, dimension = RateLimit.Dimension.TOKEN)
        public Result<Void> rateToken() {
            return Result.ok(null);
        }

        @GetMapping("/admin/test/query")
        @SaCheckPermission("blog:article:query")
        Result<Void> query() {
            return Result.ok(null);
        }

        @GetMapping("/admin/test/users")
        @SaCheckPermission("sys:user:query")
        Result<Void> users() {
            return Result.ok(null);
        }

        @GetMapping("/admin/test/no-permission")
        Result<Void> noPermission() {
            return Result.ok(null);
        }
    }
}
