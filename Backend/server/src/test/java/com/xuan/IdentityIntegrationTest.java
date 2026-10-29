package com.xuan;

import com.xuan.exception.BusinessException;
import com.xuan.mapper.UserMapper;

import cn.dev33.satoken.SaManager;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import org.springframework.http.HttpMethod;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.entity.BlogArticle;
import com.xuan.entity.BlogFile;
import com.xuan.properties.AuthProperties;
import com.xuan.result.Result;
import com.xuan.service.AuthorizationService;
import com.xuan.service.IUserService;
import com.xuan.service.JustAuthGateway;
import com.xuan.service.OAuthProviderGateway;
import com.xuan.service.RedisOAuthStore;
import com.xuan.service.SessionService;
import com.xuan.vo.ProviderVO;
import com.xuan.vo.SessionVO;
import jakarta.servlet.http.Cookie;
import me.zhyd.oauth.config.AuthDefaultSource;
import me.zhyd.oauth.utils.AuthChecker;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** 真实 MySQL/Redis、Service、Mapper 和 MVC 链；仅平台网络及确定性失败使用替身。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IdentityIntegrationTest.Boundaries.class)
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class IdentityIntegrationTest {
    private static IsolatedServices services;
    private static final String PASSWORD = "Example-password-2026";
    private static final String ORIGIN = "https://blog.example.test";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean IUserService users;
    @Autowired AuthorizationService authorization;
    @Autowired RedisOAuthStore store;
    @Autowired AuthProperties properties;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SqlSessionFactory sqlSessionFactory;
    @MockitoSpyBean SessionService sessions;

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) throws Exception {
        services = new IsolatedServices();
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
            registry.add("app.auth.providers." + source + ".redirect-uri", () -> ORIGIN + "/api/v1/auth/oauth/" + source + "/callback");
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
        reset(sessions, users);
        jdbc.update("DELETE FROM blog_article");
        jdbc.update("DELETE FROM blog_file");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("UPDATE sys_role SET status=1");
        jdbc.update("UPDATE sys_permission SET status=1");
        jdbc.update("DELETE FROM sys_role WHERE role_id > 3");
        jdbc.update("UPDATE sys_menu SET status=1");
        redis.delete(redis.keys("dl:test:*"));
    }

    @Test
    void registrationLoginSessionsWhitelistAndValidation() throws Exception {
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
        assertThat(jdbc.queryForObject("SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).startsWith("$2a$");
        assertCode(register("LEAF"), 409, "UNIQUE_CONFLICT");
        String token = login("leaf");
        String another = login("leaf");
        assertThat(another).isNotEqualTo(token);
        assertThat(StpUtil.getStpLogic().getTokenTimeout(token)).isBetween(7190L, 7200L);
        assertThat(redis.keys("dl:test:session:*")).isNotEmpty();
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("permissionCodes").size()).isEqualTo(3);
        assertCode(call("GET", "/me", null, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/blog/anything", null, null), 401, "UNAUTHENTICATED");
        assertCode(mvc.perform(req("GET", "/me").param("Authorization", "Bearer " + token)).andReturn(), 401, "UNAUTHENTICATED");
        assertCode(mvc.perform(req("GET", "/me").cookie(new Cookie("Authorization", token))).andReturn(), 401, "UNAUTHENTICATED");
        assertCode(call("POST", "/auth/register", null, "{\"username\":12}"), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/register", null, "{\"username\":\"a\",\"username\":\"b\"}"), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/auth/register", null, "{\"username\":\"leaf\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"叶\",\"status\":1}"), 400, "VALIDATION_ERROR");
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
        // 绝对 URL 也不能将本站附件 API 当头像，包括编码和路径归一化后的等价值。
        for (String avatar : List.of("javascript:alert(1)", "https://user:password@example.test/a.png",
                ORIGIN + "/api/v1/admin/files/1/content", ORIGIN + "/api/v1/blog/articles/1/files/2/content",
                ORIGIN + "/api/v1/admin/articles/1/files/2/content",
                ORIGIN + "/api/v1/%61dmin/files/1/content",
                ORIGIN + "/api/v1/temp/%2e%2e/admin/files/1/content")) {
            assertCode(patch(token, original, json.writeValueAsString(Map.of("avatar", avatar))), 400, "VALIDATION_ERROR");
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
        MvcResult filled = patch(token, current, "{\"nickname\":\" 新昵称 \",\"email\":\" leaf@example.test \",\"avatar\":\" https://images.example.test/a.png \",\"intro\":\" 简介 \"}");
        assertCode(filled, 200, "OK");
        assertThat(data(filled).get("nickname").asString()).isEqualTo("新昵称");
        assertThat(data(filled).get("email").asString()).isEqualTo("leaf@example.test");
        assertThat(data(filled).get("avatar").asString()).isEqualTo("https://images.example.test/a.png");
        long other = Long.parseLong(data(register("other")).get("userId").asString());
        jdbc.update("UPDATE sys_user SET email='used@example.test' WHERE user_id=?", other);
        String filledRevision = filled.getResponse().getHeader("X-Revision");
        assertCode(patch(token, filledRevision, "{\"nickname\":\"不应保存\",\"email\":\"USED@example.test\"}"), 409, "UNIQUE_CONFLICT");
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
                .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "Authorization,If-Revision")).andReturn();
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
                .contains("content/article/index", "\"menuType\":\"B\"", "\"isVisible\":0").doesNotContain("system/user/index");
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
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isNull();
        assertCode(call("GET", "/me", old, null), 401, "UNAUTHENTICATED");
        assertCode(call("GET", "/me", recent, null), 401, "UNAUTHENTICATED");
        assertCode(call("POST", "/auth/login", null, loginBody("leaf", PASSWORD)), 401, "AUTH_FAILED");
        OAuthFlow flow = start("LOGIN", null, null);
        String oauthToken = data(exchange(callbackTicket(flow, "verified"), flow.browser(), null)).get("session").get("accessToken").asString();
        assertCode(call("DELETE", "/me/password", oauthToken, clearBody), 409, "STATE_CONFLICT");
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", oauthToken, null), 409, "LAST_LOGIN_METHOD");
        StpUtil.getStpLogic().getTokenSessionByToken(oauthToken).set("method", "PASSWORD");
        assertCode(call("PUT", "/me/password", oauthToken, "{\"newPassword\":\"new-password-2026\"}"), 403, "REAUTH_REQUIRED");
        StpUtil.getStpLogic().getTokenSessionByToken(oauthToken).set("method", "OAUTH");
        assertCode(call("PUT", "/me/password", oauthToken, "{\"newPassword\":\"new-password-2026\",\"currentPassword\":null}"), 400, "VALIDATION_ERROR");
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
        assertCode(call("PUT", "/me/password", token, "{\"newPassword\":\"new-password-2026\",\"currentPassword\":\"" + PASSWORD + "\"}"), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isEqualTo(before);
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='rollback'", Integer.class)).isZero();
    }

    @Test
    void oauthBrowserReplayExpiryAndVerifiedIdentity() throws Exception {
        OAuthFlow flow = start("LOGIN", null, null);
        assertCode(mvc.perform(req("GET", "/auth/oauth/GITHUB/callback").param("state", flow.state())
                .param("code", "same").cookie(new Cookie("__Host-dl-oauth-browser", SessionService.random()))).andReturn(), 400, "OAUTH_STATE_INVALID");
        String ticket = callbackTicket(flow, " SameIdentity ");
        assertCode(callback(flow, "same"), 400, "OAUTH_STATE_INVALID");
        assertCode(exchange(ticket, SessionService.random(), null), 400, "OAUTH_STATE_INVALID");
        MvcResult result = exchange(ticket, flow.browser(), null);
        assertCode(result, 200, "OK");
        assertThat(data(result).has("binding")).isFalse();
        assertThat(data(result).get("session").get("user").get("hasPassword").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT uuid FROM sys_user_oauth", String.class)).isEqualTo(" SameIdentity ");
        assertCode(exchange(ticket, flow.browser(), null), 400, "OAUTH_STATE_INVALID");
        OAuthFlow expiring = start("LOGIN", null, flow.browser());
        redis.expire("dl:test:oauth:state:" + SessionService.digest(expiring.state()), Duration.ofMillis(1));
        Thread.sleep(5);
        assertCode(callback(expiring, "same"), 400, "OAUTH_STATE_INVALID");
        OAuthFlow next = start("LOGIN", null, flow.browser());
        String expires = callbackTicket(next, "another");
        redis.expire("dl:test:oauth:ticket:" + SessionService.digest(expires), Duration.ofMillis(1));
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
        assertThat(callback(conflict, "bind-identity").getResponse().getHeader("Location")).endsWith("#error=OAUTH_BIND_CONFLICT");
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
        // 在真实绑定写入后注入事务异常，验证回滚与回跳，保留会话校验和 Mapper 执行。
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new TransactionSystemException("injected transaction failure");
        }).when(users).bindOAuth(id, SessionService.digest(token), "GITHUB", "transaction-failure-identity");

        MvcResult failed = callback(bind, "transaction-failure-identity");
        assertThat(failed.getResponse().getStatus()).isEqualTo(303);
        assertThat(failed.getResponse().getHeader("Location"))
                .isEqualTo(ORIGIN + "/oauth/complete#error=SERVICE_UNAVAILABLE");
        assertThat(failed.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(failed.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?", Integer.class, id)).isZero();
        assertThat(store.containsKey(bind.state())).isFalse();
        assertThat(redis.keys("dl:test:oauth:ticket:*")).isEmpty();
        assertCode(callback(bind, "transaction-failure-identity"), 400, "OAUTH_STATE_INVALID");
    }

    @Test
    void redisAtomicConsumeMillisecondTimeoutAndRateLimit() throws Exception {
        String state = SessionService.random();
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
        assertThat(redis.getExpire("dl:test:oauth:justauth:" + SessionService.digest(state), TimeUnit.MILLISECONDS)).isBetween(1L, 30L);
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
    void eighteenMappingsAndUnsignedValuesMatchRealMysql() throws Exception {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class);
        assertThat(tables).hasSize(18);
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
                    assertThat(id.type()).isEqualTo(table.equals("blog_article_content") || table.equals("blog_site_config") ? IdType.INPUT : IdType.AUTO);
                }
            }
            List<String> keys = jdbc.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY'", String.class, table);
            assertThat(idCount).isEqualTo(keys.size() == 1 ? 1 : 0);
            assertThat(mapped).containsExactlyInAnyOrderElementsOf(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?", String.class, table));
        }
        long userId = Long.parseLong(data(register("leaf")).get("userId").asString());
        jdbc.update("INSERT INTO blog_article(author_id,title,slug,view_count) VALUES (?,'test','test',18446744073709551615)", userId);
        jdbc.update("INSERT INTO blog_file(uploader_id,original_name,object_key,mime_type,size_bytes) VALUES (?,'test','test','image/png',18446744073709551615)", userId);
        var config = sqlSessionFactory.getConfiguration();
        if (!config.hasMapper(ArticleTestMapper.class)) {
            config.addMapper(ArticleTestMapper.class);
            config.addMapper(FileTestMapper.class);
        }
        try (var session = sqlSessionFactory.openSession()) {
            assertThat(session.getMapper(ArticleTestMapper.class).selectList(null).getFirst().getViewCount()).isEqualTo(new BigInteger("18446744073709551615"));
            assertThat(session.getMapper(FileTestMapper.class).selectList(null).getFirst().getSizeBytes()).isEqualTo(new BigInteger("18446744073709551615"));
        }
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
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
                    () -> call("DELETE", "/me/password", token, "{\"currentPassword\":\"" + PASSWORD + "\"}").getResponse().getStatus(),
                    () -> call("DELETE", "/me/oauth-bindings/GITHUB", token, null).getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (var result : executor.invokeAll(changes)) {
                statuses.add(result.get());
            }
            assertThat(statuses).contains(200);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT (password_hash IS NOT NULL) + (SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?) FROM sys_user WHERE user_id=?", Integer.class, id, id)).isEqualTo(1);
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?", Integer.class, id)).isZero();
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
            assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"" + PASSWORD + "\"}"), 409, "LAST_LOGIN_METHOD");
            assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 404, "NOT_FOUND");
        } finally {
            github.setEnabled(true);
            gitee.setClientSecret(giteeSecret);
        }
        assertCode(call("DELETE", "/me/oauth-bindings/GITEE", otherToken, null), 404, "NOT_FOUND");
        assertCode(call("DELETE", "/me/oauth-bindings/github", token, null), 404, "NOT_FOUND");
        StpUtil.getStpLogic().getTokenSessionByToken(another).set("authenticatedAt", System.currentTimeMillis() - 301_000);
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", another, null), 403, "REAUTH_REQUIRED");
        assertCode(call("DELETE", "/me/password", another, "{\"currentPassword\":\"" + PASSWORD + "\"}"), 403, "REAUTH_REQUIRED");
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=? AND source='GITHUB'", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=? AND source='GITHUB'", Integer.class, otherId)).isEqualTo(1);
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
        assertCode(call("PUT", "/me/password", token, "{\"newPassword\":\"new-password-2026\",\"currentPassword\":\"wrong-password\"}"), 401, "AUTH_FAILED");
        assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"wrong-password\"}"), 401, "AUTH_FAILED");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isEqualTo(before);
        assertCode(call("PUT", "/me/password", token, "{\"newPassword\":\" new-password-2026 \",\"currentPassword\":\"" + PASSWORD + "\"}"), 200, "OK");
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
        assertCode(call("DELETE", "/me/password", token, "{\"currentPassword\":\"" + PASSWORD + "\"}"), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE user_id=?", String.class, id)).isEqualTo(before);
        assertCode(call("DELETE", "/me/oauth-bindings/GITHUB", token, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_oauth WHERE user_id=?", Integer.class, id)).isEqualTo(1);
        assertCode(call("GET", "/me", token, null), 200, "OK");
    }

    @Test
    void permissionsAndMenusCombineEffectiveRolesAndRequireTheWholeAncestorChain() throws Exception {
        long id = Long.parseLong(data(register("leaf")).get("userId").asString());
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES (9,'pages','菜单','ALL'),(10,'access','权限','SELF')");
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
        assertThat(data(menus).get(0).get("children").get(0).get("children").get(0).get("menuId").asString()).isEqualTo("1011");
        assertThat(menus.getResponse().getContentAsString()).contains("\"isVisible\":0").doesNotContain("system", "comment");
        JsonNode capabilities = data(call("GET", "/me/capabilities", token, null));
        assertThat(capabilities.get("roleCodes").toString()).isEqualTo("[\"access\",\"member\",\"pages\"]");
        assertThat(capabilities.get("permissionCodes").toString())
                .isEqualTo("[\"admin:access\",\"blog:article:create\",\"blog:article:like\",\"blog:article:query\",\"blog:comment:create\",\"blog:comment:deleteOwn\"]");
        assertThat(capabilities.get("dataScopes").get("blog:article:query").asString()).isEqualTo("SELF");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES (9,10)");
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("dataScopes").get("blog:article:query").asString()).isEqualTo("ALL");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=10");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("dataScopes").has("blog:article:query")).isFalse();
        jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=10");
        jdbc.update("UPDATE sys_menu SET status=0 WHERE menu_id=100");
        assertThat(data(call("GET", "/me/menus", token, null)).isEmpty()).isTrue();
        assertThat(authorization.requireScope(id, "blog:article:query")).isEqualTo("ALL");
        jdbc.update("UPDATE sys_role SET status=0 WHERE role_id=9");
        assertThat(authorization.requireScope(id, "blog:article:query")).isEqualTo("SELF");
        assertThat(data(call("GET", "/me/capabilities", token, null)).get("roleCodes").toString()).isEqualTo("[\"access\",\"member\"]");
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
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(9,'operator','操作员','SELF')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,9)", id);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,80)");
        assertCode(call("GET", "/admin/users", token, null), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(9,1)");
        assertCode(call("GET", "/admin/users", token, null), 200, "OK");
        // 查询权限不能授予创建、修改和授权；入口身份与权限每次重新读取。
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
        String withEmail = createUserBody("email_user").replace("\"nickname\":\"叶\"", "\"nickname\":\"  昵称  \",\"email\":\" used@example.test \"");
        MvcResult emailed = call("POST", "/admin/users", admin, withEmail);
        assertCode(emailed, 201, "OK");
        assertThat(data(emailed).get("nickname").asString()).isEqualTo("昵称");
        assertThat(data(emailed).get("email").asString()).isEqualTo("used@example.test");
        assertCode(call("POST", "/admin/users", admin, createUserBody("NEW_USER")), 409, "UNIQUE_CONFLICT");
        assertCode(call("POST", "/admin/users", admin, withEmail.replace("email_user", "email_other")), 409, "UNIQUE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='email_other'", Integer.class)).isZero();
        String valid = createUserBody("invalid_user");
        for (String extra : List.of("\"roleIds\":[]", "\"status\":null", "\"status\":2", "\"status\":\"1\"", "\"status\":1.5",
                "\"email\":\"bad\"", "\"avatar\":\"javascript:bad\"", "\"avatar\":\"" + ORIGIN + "/api/v1/admin/files/1/content\"",
                "\"userId\":\"1\"", "\"nickname\":12")) {
            assertCode(call("POST", "/admin/users", admin, valid.substring(0, valid.length() - 1) + "," + extra + "}"), 400, "VALIDATION_ERROR");
        }
        assertCode(call("POST", "/admin/users", admin, valid.replace(PASSWORD, "密".repeat(25))), 400, "VALIDATION_ERROR");
        assertCode(call("POST", "/admin/users", admin, valid.replace(PASSWORD, "short")), 400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='invalid_user'", Integer.class)).isZero();
        MvcResult disabled = call("POST", "/admin/users", admin, valid.substring(0, valid.length() - 1) + ",\"status\":0}");
        assertCode(disabled, 201, "OK");
        assertCode(call("POST", "/auth/login", null, loginBody("invalid_user", PASSWORD)), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void adminUserPaginationUsesLiteralFiltersAndStableOrder() throws Exception {
        String admin = adminLogin("adminroot");
        long first = Long.parseLong(data(register("first_user")).get("userId").asString());
        long second = Long.parseLong(data(register("second_user")).get("userId").asString());
        jdbc.update("UPDATE sys_user SET nickname='literal%_\\\\',create_time='2026-01-01' WHERE user_id IN (?,?)", first, second);
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
            assertCode(adminPut(path, admin, revision, userBody("叶", 1).replace("\"" + field + "\":null,", "")), 400, "VALIDATION_ERROR");
        }
        for (String extra : List.of("\"username\":\"changed\"", "\"password\":\"12345678\"", "\"roleIds\":[]")) {
            String body = userBody("叶", 1);
            assertCode(adminPut(path, admin, revision, body.substring(0, body.length() - 1) + "," + extra + "}"), 400, "VALIDATION_ERROR");
        }
        MvcResult updated = adminPut(path, admin, revision, userBody("  修改昵称  ", 1).replace("\"intro\":null", "\"intro\":\"  \""));
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
                json.writeValueAsString(Map.of("roleIds", IntStream.range(1, 102).mapToObj(Integer::toString).toList())))) {
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
                    "SELECT permission_code FROM sys_permission WHERE permission_code='admin:access' OR permission_code LIKE 'sys:%'", String.class))).isEqualTo(1);
        }
        assertCode(adminPut("/admin/users/" + id, admin, revision, userBody("不应保存", 0)), 409, "LAST_ADMIN");
        assertCode(adminPut("/admin/users/" + id + "/roles", admin, revision, "{\"roleIds\":[\"3\"]}"), 409, "LAST_ADMIN");
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
        doThrow(new RedisConnectionFailureException("injected external failure")).when(sessions).revokeAll(Long.parseLong(id));
        assertCode(adminPut("/admin/users/" + id, admin, revision, userBody("不应保存", 0)), 503, "SERVICE_UNAVAILABLE");
        assertCode(adminPut("/admin/users/" + id + "/roles", admin, revision, "{\"roleIds\":[\"2\"]}"), 503, "SERVICE_UNAVAILABLE");
        assertThat(adminRevision(admin, id)).isEqualTo(revision);
        assertThat(data(call("GET", "/admin/users/" + id, admin, null)).get("roleIds").toString()).isEqualTo("[\"3\"]");
        assertCode(call("GET", "/me", token, null), 200, "OK");
        reset(sessions);
        jdbc.update("UPDATE sys_user SET email='used@example.test' WHERE username='adminroot'");
        assertCode(adminPut("/admin/users/" + id, admin, revision,
                userBody("不应保存", 0).replace("\"email\":null", "\"email\":\"USED@example.test\"")), 409, "UNIQUE_CONFLICT");
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
                assertCode(response, response.getResponse().getStatus(), response.getResponse().getStatus() == 200 ? "OK" : "LAST_ADMIN");
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON u.user_id=ur.user_id WHERE u.status=1 AND ur.role_id=1", Integer.class)).isEqualTo(1);
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
                }).when(users).adminUpdate(eq(Long.parseLong(target)), any(AdminUserUpdateDTO.class), eq(revision));
                // 主线程先持有真实保护锁；请求通过 MVC 鉴权后进入 Service，随后等待此锁。
                var pending = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_id=1 FOR UPDATE", Long.class);
                    var response = executor.submit(() -> adminPut("/admin/users/" + target, admin, revision, userBody("不应保存", 1)));
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
                assertCode(pending.get(10, TimeUnit.SECONDS), revoke ? 401 : 403, revoke ? "UNAUTHENTICATED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT nickname FROM sys_user WHERE user_id=?", String.class, target)).isEqualTo("叶");
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", actor);
            }
        }
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
        return call("POST", "/auth/register", null, "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"叶\"}");
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
        return request(HttpMethod.valueOf(method), "/api/v1" + path).contextPath("/api/v1").servletPath(path);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private void assertCode(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("code").asString()).isEqualTo(code);
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
        String state = URLDecoder.decode(url.substring(url.indexOf("state=") + 6).split("&")[0], StandardCharsets.UTF_8);
        return new OAuthFlow(state, cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';')));
    }

    private MvcResult callback(OAuthFlow flow, String identity) throws Exception {
        return mvc.perform(req("GET", "/auth/oauth/GITHUB/callback").param("state", flow.state()).param("code", identity)
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

    record OAuthFlow(String state, String browser) { }
    interface ArticleTestMapper extends BaseMapper<BlogArticle> { }
    interface FileTestMapper extends BaseMapper<BlogFile> { }

    /** 平台网络是唯一身份替身，state 校验和平台 URL 仍走真实 JustAuth。 */
    @TestConfiguration
    static class Boundaries {
        @Bean
        @Primary
        OAuthProviderGateway platform(JustAuthGateway actual, RedisOAuthStore cache) {
            return new OAuthProviderGateway() {
                @Override
                public List<ProviderVO> providers() { return actual.providers(); }
                @Override
                public Set<String> enabledSources() { return actual.enabledSources(); }
                @Override
                public void requireEnabled(String source) { actual.requireEnabled(source); }
                @Override
                public String authorize(String source, String state) { return actual.authorize(source, state); }
                @Override
                public Identity verify(String source, String state, String code) {
                    AuthChecker.checkState(state, AuthDefaultSource.valueOf(source), cache);
                    return new Identity(code, "😀".repeat(70), "javascript:invalid");
                }
            };
        }

        @Bean
        TestAdminController testAdminController() { return new TestAdminController(); }
    }

    /** 仅测试 classpath 的入口，检验真实 Sa-Token 过滤和注解组合。 */
    @RestController
    static class TestAdminController {
        @GetMapping("/admin/test/query")
        @SaCheckPermission("blog:article:query")
        Result<Void> query() { return Result.ok(null); }
        @GetMapping("/admin/test/users")
        @SaCheckPermission("sys:user:query")
        Result<Void> users() { return Result.ok(null); }
        @GetMapping("/admin/test/no-permission")
        Result<Void> noPermission() { return Result.ok(null); }
    }
}
