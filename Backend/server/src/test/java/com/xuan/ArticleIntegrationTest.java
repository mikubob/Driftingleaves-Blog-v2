package com.xuan;

import com.xuan.audit.FailureAuditWriter;
import com.xuan.client.MailClient;
import com.xuan.entity.SysOperationLog;
import com.xuan.mapper.ArticleMapper;
import com.xuan.properties.FileProperties;
import com.xuan.security.AuthorizationProvider;
import com.xuan.session.SessionManager;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 第7..11章走真实 MVC、Service、Mapper 和隔离 MySQL/Redis，不替换核心业务，也不连接现有服务。
 */
@SpringBootTest
@org.springframework.context.annotation.Import(CacheQueryProbe.Config.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class ArticleIntegrationTest {

    private static IsolatedServices services;
    private static final String PASSWORD = "Example-password-2026";
    private static final String ORIGIN = "https://blog.example.test";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired CacheQueryProbe cacheProbe;
    @Autowired org.springframework.cache.CacheManager publicCaches;
    @Autowired com.xuan.cache.PublicCacheStore publicCacheStore;
    @Autowired com.xuan.service.ISocialService publicSocials;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    FileProperties files;

    @Autowired
    ArticleMapper articles;

    @Autowired
    SessionManager sessions;

    @Autowired
    com.xuan.properties.PublicSiteProperties sites;

    @MockitoSpyBean
    FailureAuditWriter failures;

    @MockitoBean
    MailClient mail;

    @MockitoSpyBean(name = "mailExecutor")
    ExecutorService mailTasks;
    private final List<Future<?>> pendingMail = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<Void>> pending = new CopyOnWriteArrayList<>();

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
        registry.add("app.auth.redis-prefix", () -> "dl:article-test:");
        registry.add("app.auth.bcrypt-strength", () -> 4);
        registry.add("app.auth.allowed-origins", () -> ORIGIN);
        registry.add("app.auth.frontend-complete-url", () -> ORIGIN + "/oauth/complete");
        registry.add("app.sites.blog-base-url", () -> ORIGIN);
        registry.add("app.sites.home-base-url", () -> "https://home.example.test");
        registry.add("app.sites.cv-base-url", () -> "https://cv.example.test");
        registry.add("app.sites.api-base-url", () -> ORIGIN + "/api/v1");
        registry.add("app.mail.confirm-url", () -> ORIGIN + "/subscription/confirm");
        registry.add("app.mail.unsubscribe-url", () -> ORIGIN + "/subscription/unsubscribe");
        registry.add("app.auth.rate.register", () -> 200);
        registry.add("app.files.local-root", () -> services.directory.resolve("uploads").toString());
    }

    @AfterAll
    static void stop(@Autowired ConfigurableApplicationContext context) {
        context.getBean("mailExecutor", ExecutorService.class).close();
        context.getBean(LettuceConnectionFactory.class).destroy();
        context.getBean(HikariDataSource.class).close();
        if (services != null) {
            services.close();
        }
    }

    @BeforeEach
    void resetData() throws Exception {
        cacheProbe.reset();
        reset(failures, mail, mailTasks);
        doAnswer(invocation -> {
            Future<?> future = (Future<?>) invocation.callRealMethod();
            pendingMail.add(future);
            return future;
        }).when(mailTasks).submit(any(Runnable.class));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<Void> result = (CompletableFuture<Void>) invocation.callRealMethod();
            pending.add(result);
            return result;
        }).when(failures).write(any(SysOperationLog.class));
        jdbc.execute("DROP TRIGGER IF EXISTS part7_fail");
        jdbc.update("DELETE FROM sys_operation_log");
        while (jdbc.update(
                "DELETE c FROM blog_comment c LEFT JOIN blog_comment child ON child.parent_id=c.comment_id WHERE " +
                 "child.comment_id IS NULL") > 0) {
        }
        jdbc.update("DELETE FROM blog_article");
        jdbc.update("DELETE FROM blog_file");
        jdbc.update("DELETE FROM blog_category");
        jdbc.update("DELETE FROM blog_tag");
        publicCaches.getCache("tags").clear();
        while (jdbc.update(
                "DELETE m FROM blog_message m LEFT JOIN blog_message child ON child.parent_id=m.message_id WHERE " +
                 "child.message_id IS NULL") > 0) {
        }
        jdbc.update("DELETE FROM blog_page_view");
        jdbc.update("DELETE FROM blog_visitor");
        jdbc.update("DELETE FROM blog_rss_subscription");
        jdbc.update("DELETE FROM home_profile");
        jdbc.update("DELETE FROM home_social_link");
        jdbc.update("DELETE FROM cv_experience");
        jdbc.update("DELETE FROM cv_skill");
        jdbc.update("DELETE FROM blog_music");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("DELETE FROM sys_role WHERE role_id > 3");
        jdbc.update("UPDATE sys_role SET status=1, data_scope=CASE WHEN role_id=1 THEN 'ALL' ELSE 'SELF' END");
        jdbc.update("UPDATE sys_permission SET status=1");
        jdbc.update(
                "INSERT INTO blog_site_config(config_key,config_value,is_public) VALUES('comment',JSON_OBJECT" +
                 "('enabled',TRUE,'reviewRequired',TRUE),0) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)" +
                  ",is_public=0");
        jdbc.update("DELETE FROM blog_friend_link");
        jdbc.update("DELETE FROM blog_site_config WHERE config_key <> 'comment'");
        jdbc.update("UPDATE blog_site_config SET description=NULL WHERE config_key='comment'");
        jdbc.update(
                "INSERT INTO blog_site_config(config_key,config_value,is_public) VALUES('site',JSON_OBJECT('title'," +
                 "'DriftingLeaves','description','记录与分享','icp','','publicSecurityRecord','','startDate',NULL),1)," +
                  "('message',JSON_OBJECT('enabled',TRUE,'reviewRequired',TRUE),0)");
        // 每个测试先恢复初始化授权，避免上一条删改权限影响下一条。
        jdbc.update(
                "INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT 1,permission_id FROM " +
                 "sys_permission");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN(2,3)");
        jdbc.update(
                "INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT 2,permission_id FROM " +
                 "sys_permission WHERE permission_id IN(1,10,11,12,13,14,15,20,30,43,44,45,60,61,62,163,164,165,183," +
                  "184)");
        jdbc.update(
                "INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT 3,permission_id FROM " +
                 "sys_permission WHERE permission_id IN(43,44,45,163,164,165,183,184)");
        redis.delete(redis.keys("dl:article-test:*"));
        Files.createDirectories(files.getLocalRoot());
        for (Path artifact : uploadArtifacts()) {
            Files.deleteIfExists(artifact);
        }
    }

    @AfterEach
    void finishAudits() throws Exception {
        for (CompletableFuture<Void> audit : pending) {
            audit.get(10, TimeUnit.SECONDS);
        }
        pending.clear();
        for (Future<?> task : pendingMail) {
            task.get(10, TimeUnit.SECONDS);
        }
        pendingMail.clear();
    }

    @Test
    void createsDraftWithDefaultsCurrentAuthorSafeHtmlAndTransactionalAudit() throws Exception {
        String token = actor("author_defaults", 2);
        String markdown = "# 标题\n\n你好。 [word](https://example.test/target)\n\n<script>bad()</script><img src=x " +
         "onerror=alert(1)>";
        MvcResult created = call("POST", "/admin/articles", token,
                json.writeValueAsString(Map.of("title", " 🌿 ", "slug", "First-Leaf", "markdownContent", markdown)));
        code(created, 201, "OK");
        JsonNode value = data(created);
        String id = value.get("articleId").asString();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/articles/" + id);
        assertThat(value.get("title").asString()).isEqualTo("🌿");
        assertThat(value.get("authorId").asString()).isEqualTo(userId("author_defaults"));
        assertThat(value.get("status").asInt()).isZero();
        assertThat(value.get("visibility").asInt()).isEqualTo(1);
        assertThat(value.get("articleType").asInt()).isEqualTo(1);
        assertThat(value.get("allowComment").asInt()).isEqualTo(1);
        assertThat(value.get("summary").asString()).isEmpty();
        assertThat(value.get("viewCount").asString()).isEqualTo("0");
        assertThat(value.get("categoryId").isNull()).isTrue();
        assertThat(value.get("publishTime").isNull()).isTrue();
        assertThat(value.get("tagIds").isEmpty()).isTrue();
        assertThat(value.get("markdownContent").asString()).isEqualTo(markdown);
        assertThat(value.get("htmlContent").asString()).doesNotContain("<script", "onerror", "javascript:");
        assertThat(value.get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(revision(token, id)).matches("\"[0-9a-f]{64}\"");
        assertThat(jdbc.queryForObject(
                "SELECT target_id FROM sys_operation_log WHERE operation_target='blog:article' AND result=1",
                Long.class)).isEqualTo(Long.valueOf(id));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:article' AND error_message LIKE " +
                 "'%bad%'",
                Integer.class)).isZero();
        code(call("GET", "/blog/articles/First-Leaf", token, null), 404, "NOT_FOUND");
    }

    @Test
    void rejectsUnknownDuplicateWrongTypesIdsUnicodeLimitsAndLargeMarkdown() throws Exception {
        String token = actor("strict", 1);
        Map<String, Object> body = body("Strict", "source");
        for (String field : List.of("authorId", "status", "htmlContent", "publishTime", "deletedAt", "viewCount",
                "createTime", "present", "completeReplace", "uniqueTags")) {
            Map<String, Object> changed = new LinkedHashMap<>(body);
            changed.put(field, "1");
            code(call("POST", "/admin/articles", token, json.writeValueAsString(changed)), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/articles", token,
                "{\"title\":\"a\",\"title\":\"b\",\"slug\":\"s\",\"markdownContent\":\"\"}"), 400, "VALIDATION_ERROR");
        Map<String, List<String>> invalid = Map.ofEntries(
                Map.entry("title", List.of("null", "12", "\"  \"", json.writeValueAsString("🌿".repeat(201)))),
                Map.entry("slug", List.of("null", "\"a--b\"", "\"中文\"", json.writeValueAsString("a".repeat(201)))),
                Map.entry("summary", List.of("null", "true", json.writeValueAsString("🌿".repeat(501)))),
                Map.entry("categoryId", List.of("1", "\"01\"", "\"0\"", "\"-1\"", "\"9223372036854775808\"")),
                Map.entry("articleType", List.of("null", "4", "1.2", "\"1\"", "true")),
                Map.entry("visibility", List.of("null", "0", "3", "\"1\"")),
                Map.entry("isTop", List.of("null", "2", "true")),
                Map.entry("allowComment", List.of("null", "-1", "\"1\"")),
                Map.entry("markdownContent", List.of("null", "12")),
                Map.entry("tagIds",
                        List.of("null", "[1]", "[null]", "[\"01\"]", "[\"1\",\"1\"]", "[\"9223372036854775808\"]")));
        for (var entry : invalid.entrySet()) {
            for (String value : entry.getValue()) {
                Map<String, Object> changed = new LinkedHashMap<>(body);
                changed.remove(entry.getKey());
                String encoded = json.writeValueAsString(changed);
                code(call("POST", "/admin/articles", token,
                                encoded.substring(0, encoded.length() - 1) + ",\"" + entry.getKey() + "\":" + value + "}"), 400,
                        "VALIDATION_ERROR");
            }
        }
        body.put("title", "🌿".repeat(200));
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body)), 201, "OK");
        body.put("slug", "Too-Large");
        body.put("markdownContent", "🌿".repeat(262145));
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body)), 413, "PAYLOAD_TOO_LARGE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article", Integer.class)).isEqualTo(1);
    }

    @Test
    void fullPutRequiresEveryFieldSupportsNullAndVersionsBodyAndSortedTags() throws Exception {
        String token = actor("put", 1);
        seedDictionary();
        String id = create(token, "Put", "old");
        String original = revision(token, id);
        Map<String, Object> body = body("Put", "new");
        for (String field : new ArrayList<>(body.keySet())) {
            Map<String, Object> missing = new LinkedHashMap<>(body);
            missing.remove(field);
            code(write("PUT", "/admin/articles/" + id, token, original, missing), 400, "VALIDATION_ERROR");
        }
        body.put("categoryId", "700");
        body.put("coverUrl", "https://example.test/cover.png");
        body.put("tagIds", List.of("702", "701"));
        MvcResult saved = write("PUT", "/admin/articles/" + id, token, original, body);
        code(saved, 200, "OK");
        assertThat(data(saved).get("tagIds").toString()).isEqualTo("[\"701\",\"702\"]");
        String changed = saved.getResponse().getHeader("X-Revision");
        assertThat(changed).isNotEqualTo(original);
        body.put("tagIds", List.of("701", "702"));
        code(write("PUT", "/admin/articles/" + id, token, changed, body), 200, "OK");
        assertThat(revision(token, id)).isEqualTo(changed);
        body.put("markdownContent", "only body changed");
        code(write("PUT", "/admin/articles/" + id, token, changed, body), 200, "OK");
        String bodyVersion = revision(token, id);
        assertThat(bodyVersion).isNotEqualTo(changed);
        body.put("categoryId", null);
        body.put("coverUrl", null);
        body.put("tagIds", List.of());
        MvcResult cleared = write("PUT", "/admin/articles/" + id, token, bodyVersion, body);
        code(cleared, 200, "OK");
        assertThat(data(cleared).get("categoryId").isNull()).isTrue();
        assertThat(data(cleared).get("coverUrl").isNull()).isTrue();
        assertThat(data(cleared).get("tags").isEmpty()).isTrue();
    }

    @Test
    void associationsAndUniqueConflictsRollbackEntireAggregateIncludingAudit() throws Exception {
        String token = actor("rollback", 1);
        seedDictionary();
        String id = create(token, "Existing", "old");
        String version = revision(token, id);
        Map<String, Object> body = body("Changed", "new");
        for (String missing : List.of("categoryId", "tagIds")) {
            Map<String, Object> invalid = new LinkedHashMap<>(body);
            invalid.put(missing, "tagIds".equals(missing) ? List.of("701", "999999") : "999999");
            code(call("POST", "/admin/articles", token, json.writeValueAsString(invalid)), 400, "VALIDATION_ERROR");
            code(write("PUT", "/admin/articles/" + id, token, version, invalid), 400, "VALIDATION_ERROR");
        }
        create(token, "Occupied", "other");
        body.put("slug", "Occupied");
        code(write("PUT", "/admin/articles/" + id, token, version, body), 409, "UNIQUE_CONFLICT");
        assertThat(revision(token, id)).isEqualTo(version);
        body.put("slug", "Changed");
        body.put("tagIds", List.of("701"));
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON blog_article_tag FOR EACH ROW SIGNAL SQLSTATE '45000' SET" +
                 " MESSAGE_TEXT='isolated insert failure'");
        code(write("PUT", "/admin/articles/" + id, token, version, body), 503, "SERVICE_UNAVAILABLE");
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body)), 503, "SERVICE_UNAVAILABLE");
        assertThat(revision(token, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?",
                String.class, id)).isEqualTo("old");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:article' AND result=1",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void revisionChecksPrecedeStateAndIdempotenceAndExcludeCountersAuditAndCache() throws Exception {
        String token = actor("revision", 1);
        String id = create(token, "Revision", "body");
        String version = revision(token, id);
        for (String path : List.of("/admin/articles/" + id, "/admin/articles/" + id + "/status",
                "/admin/articles/" + id + "/restore")) {
            String method = path.endsWith("restore") ? "POST" : "PUT";
            Object body = path.endsWith("status") ? Map.of("status", 1) : path.endsWith("restore") ? null : body(
                    "Revision", "body");
            code(write(method, path, token, null, body), 428, "PRECONDITION_REQUIRED");
            for (String invalid : List.of("*", "abc", "\"a\",\"b\"", "\"" + "A".repeat(64) + "\"")) {
                code(write(method, path, token, invalid, body), 400, "VALIDATION_ERROR");
            }
            code(write(method, path, token, "\"" + "0".repeat(64) + "\"", body), 412, "REVISION_CONFLICT");
        }
        code(write("DELETE", "/admin/articles/" + id, token, null, null), 428, "PRECONDITION_REQUIRED");
        jdbc.update(
                "UPDATE blog_article SET view_count=100, update_time=DATE_ADD(update_time,INTERVAL 1 SECOND) WHERE " +
                 "article_id=?",
                id);
        jdbc.update("UPDATE blog_article_content SET html_content='<script>bad()</script>' WHERE article_id=?", id);
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", id, userId("revision"));
        assertThat(revision(token, id)).isEqualTo(version);
        code(write("PUT", "/admin/articles/" + id + "/status", token, version, Map.of("status", 1)), 200, "OK");
        String published = revision(token, id);
        code(write("PUT", "/admin/articles/" + id + "/status", token, version, Map.of("status", 1)), 412,
                "REVISION_CONFLICT");
        code(write("PUT", "/admin/articles/" + id + "/status", token, published, Map.of("status", 1)), 200, "OK");
        assertThat(revision(token, id)).isEqualTo(published);
        code(call("GET", "/blog/articles/Revision", null, null), 200, "OK");
        assertThat(revision(token, id)).isEqualTo(published);
    }

    @Test
    void stateLifecyclePreservesFirstPublishAndTrashDataAndNeverRestoresPublic() throws Exception {
        String token = actor("states", 1);
        seedDictionary();
        String id = create(token, "States", "body");
        code(write("DELETE", "/admin/articles/" + id, token, revision(token, id), Map.of("authorId", "1")), 400,
                "VALIDATION_ERROR");
        code(write("POST", "/admin/articles/" + id + "/restore", token, revision(token, id), Map.of("status", 1)), 400,
                "VALIDATION_ERROR");
        code(status(token, id, 2), 409, "STATE_CONFLICT");
        code(write("POST", "/admin/articles/" + id + "/restore", token, revision(token, id), null), 409,
                "STATE_CONFLICT");
        MvcResult published = status(token, id, 1);
        code(published, 200, "OK");
        String first = data(published).get("publishTime").asString();
        code(status(token, id, 2), 200, "OK");
        code(status(token, id, 1), 200, "OK");
        assertThat(data(call("GET", "/admin/articles/" + id, token, null)).get("publishTime").asString()).isEqualTo(
                first);
        jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", id);
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", id, userId("states"));
        code(write("DELETE", "/admin/articles/" + id, token, revision(token, id), null), 200, "OK");
        String trash = revision(token, id);
        code(write("DELETE", "/admin/articles/" + id, token, trash, null), 200, "OK");
        code(write("PUT", "/admin/articles/" + id, token, trash, body("States", "new")), 409, "STATE_CONFLICT");
        code(write("PUT", "/admin/articles/" + id + "/status", token, trash, Map.of("status", 1)), 409,
                "STATE_CONFLICT");
        code(call("GET", "/blog/articles/States", token, null), 404, "NOT_FOUND");
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body("States", "duplicate"))), 409,
                "UNIQUE_CONFLICT");
        MvcResult restored = write("POST", "/admin/articles/" + id + "/restore", token, trash, null);
        code(restored, 200, "OK");
        assertThat(data(restored).get("status").asInt()).isEqualTo(2);
        assertThat(data(restored).get("publishTime").asString()).isEqualTo(first);
        assertThat(data(restored).get("markdownContent").asString()).isEqualTo("body");
        assertThat(data(restored).get("tagIds").toString()).isEqualTo("[\"701\"]");
        assertThat(data(restored).get("likeCount").asString()).isEqualTo("1");
        code(call("GET", "/blog/articles/States", null, null), 404, "NOT_FOUND");
        code(status(token, id, 1), 200, "OK");
    }

    @Test
    void publishingChecksNonblankBodySourceAndDoesNotAcceptServerFields() throws Exception {
        String token = actor("publish_checks", 1);
        String id = create(token, "Blank", " \n\t");
        code(status(token, id, 1), 400, "VALIDATION_ERROR");
        assertThat(data(call("GET", "/admin/articles/" + id, token, null)).get("publishTime").isNull()).isTrue();
        Map<String, Object> body = body("Source", "body");
        body.put("articleType", 2);
        for (Object url : new Object[]{null, "javascript:alert(1)", "https://user:pass@example.test/x", "/relative"}) {
            body.put("originalUrl", url);
            code(call("POST", "/admin/articles", token, json.writeValueAsString(body)), 400, "VALIDATION_ERROR");
        }
        body.put("originalUrl", "https://example.test/source");
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body)), 201, "OK");
        for (Object invalid : List.of(0, 3, "1", true)) {
            code(write("PUT", "/admin/articles/" + id + "/status", token, revision(token, id),
                    Map.of("status", invalid)), 400, "VALIDATION_ERROR");
        }
        code(write("PUT", "/admin/articles/" + id + "/status", token, revision(token, id),
                Map.of("status", 1, "publishTime", "2020-01-01T00:00:00.000Z")), 400, "VALIDATION_ERROR");
    }

    @Test
    void everyAdminRouteRequiresBothPermissionsAndSelfIsEnforcedInSql() throws Exception {
        String admin = actor("admin", 1);
        String author = actor("self", 2);
        String other = create(admin, "Other", "body");
        String own = create(author, "Own", "body");
        String member = actor("member", 3);
        for (String token : new String[]{null, member}) {
            int expected = token == null ? 401 : 403;
            String error = token == null ? "UNAUTHENTICATED" : "FORBIDDEN";
            for (String path : List.of("/admin/articles", "/admin/articles/" + own)) {
                code(call("GET", path, token, null), expected, error);
            }
            code(call("POST", "/admin/articles", token, json.writeValueAsString(body("Denied", "body"))), expected,
                    error);
            code(write("PUT", "/admin/articles/" + own, token, null, body("Denied", "body")), expected, error);
            code(write("PUT", "/admin/articles/" + own + "/status", token, null, Map.of("status", 1)), expected, error);
            code(write("DELETE", "/admin/articles/" + own, token, null, null), expected, error);
            code(write("POST", "/admin/articles/" + own + "/restore", token, null, null), expected, error);
        }
        code(call("GET", "/admin/articles/" + other, author, null), 404, "NOT_FOUND");
        code(write("PUT", "/admin/articles/" + other, author, null, body("Other", "body")), 404, "NOT_FOUND");
        code(write("PUT", "/admin/articles/" + other + "/status", author, null, Map.of("status", 1)), 404, "NOT_FOUND");
        code(write("DELETE", "/admin/articles/" + other, author, null, null), 404, "NOT_FOUND");
        code(write("POST", "/admin/articles/" + other + "/restore", author, null, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/admin/articles?authorId=" + userId("admin"), author, null)).get("total")
                .asString()).isEqualTo("0");
        assertThat(data(call("GET", "/admin/articles", author, null)).get("records").get(0).get("articleId")
                .asString()).isEqualTo(own);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=2 AND permission_id=12");
        code(write("PUT", "/admin/articles/" + own, author, revision(author, own), body("Own", "new")), 403,
                "FORBIDDEN");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=1 AND permission_id=1");
        code(call("GET", "/admin/articles", admin, null), 403, "FORBIDDEN");
    }

    @Test
    void publishedUpdateUsesIndependentPublishScopeAndUnrelatedAllCannotExpandSelf() throws Exception {
        String admin = actor("scope_admin", 1);
        String author = actor("scope_author", 2);
        String id = create(admin, "Scoped", "body");
        code(status(admin, id, 1), 200, "OK");
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(800,'article_update_all','编辑全部'," +
                 "'ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(800,12)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,800)", userId("scope_author"));
        code(write("PUT", "/admin/articles/" + id, author, null, body("Scoped", "new")), 404, "NOT_FOUND");
        code(write("PUT", "/admin/articles/" + id, author, revision(admin, id), body("Scoped", "new")), 404,
                "NOT_FOUND");
        code(call("GET", "/admin/articles/" + id, author, null), 404, "NOT_FOUND");
        code(status(admin, id, 2), 200, "OK");
        code(write("PUT", "/admin/articles/" + id, author, revision(admin, id), body("Scoped", "new")), 200, "OK");
        // 无关角色的 ALL 不能扩大 query，明确给它 query 后才会扩大。
        assertThat(data(call("GET", "/admin/articles", author, null)).get("total").asString()).isEqualTo("0");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(800,10)");
        code(call("GET", "/admin/articles/" + id, author, null), 200, "OK");
    }

    @Test
    void allPublicReadsKeepPublicConditionAndHideOnlyCategoryProjection() throws Exception {
        String token = actor("public_filter", 1);
        seedDictionary();
        String publicId = create(token, "Public", "body");
        String hiddenCategory = create(token, "Hidden-Category", "body");
        jdbc.update("UPDATE blog_article SET category_id=700 WHERE article_id=?", hiddenCategory);
        code(status(token, publicId, 1), 200, "OK");
        code(status(token, hiddenCategory, 1), 200, "OK");
        jdbc.update("UPDATE blog_category SET status=0 WHERE category_id=700");
        publicCaches.getCache("articleDetail").clear(); publicCaches.getCache("categories").clear();
        String draft = create(token, "Draft", "body");
        String privateId = create(token, "Private", "body");
        String down = create(token, "Down", "body");
        String trash = create(token, "Trash", "body");
        String future = create(token, "Future", "body");
        jdbc.update("UPDATE blog_article SET status=1,visibility=2,publish_time=UTC_TIMESTAMP(3) WHERE article_id=?",
                privateId);
        jdbc.update("UPDATE blog_article SET status=2,publish_time=UTC_TIMESTAMP(3) WHERE article_id=?", down);
        jdbc.update(
                "UPDATE blog_article SET status=1,deleted_at=UTC_TIMESTAMP(3),publish_time=UTC_TIMESTAMP(3) WHERE " +
                 "article_id=?",
                trash);
        jdbc.update(
                "UPDATE blog_article SET status=1,publish_time=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY) WHERE " +
                 "article_id=?",
                future);
        for (String slug : List.of("Draft", "Private", "Down", "Trash", "Future", "public")) {
            code(call("GET", "/blog/articles/" + slug, token, null), 404, "NOT_FOUND");
        }
        JsonNode list = data(call("GET", "/blog/articles", null, null));
        assertThat(list.get("total").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles?categoryId=700", null, null)).get("total").asString()).isEqualTo(
                "0");
        assertThat(data(call("GET", "/blog/articles?categoryId=999999", null, null)).get("total").asString()).isEqualTo(
                "0");
        assertThat(data(call("GET", "/blog/articles?tagId=999999", null, null)).get("total").asString()).isEqualTo("0");
        MvcResult detail = call("GET", "/blog/articles/Hidden-Category", null, null);
        code(detail, 200, "OK");
        assertThat(data(detail).get("category").isNull()).isTrue();
        assertThat(data(detail).get("relatedArticles").isEmpty()).isTrue();
        assertThat(data(detail).has("markdownContent")).isFalse();
        assertThat(data(detail).has("status")).isFalse();
        assertThat(data(detail).has("deletedAt")).isFalse();
        assertThat(data(detail).get("author").has("email")).isFalse();
        assertThat(data(call("GET", "/blog/article-rankings", token, null)).size()).isEqualTo(2);
        assertThat(data(call("GET", "/blog/archives", token, null)).get("records").get(0).get("articleCount")
                .asString()).isEqualTo("2");
        code(call("GET", "/blog/articles/Public/files/1/content", null, null), 400, "VALIDATION_ERROR");
        code(call("GET", "/blog/unimplemented", null, null), 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                draft)).isZero();
    }

    @Test
    void searchIsLiteralAcrossMarkdownTitleSummaryWithAndFiltersAndStablePagination() throws Exception {
        String token = actor("search", 1);
        seedDictionary();
        String first = create(token, "Search-First", "literal %_\\ marker");
        String second = create(token, "Search-Second", "other");
        code(status(token, first, 1), 200, "OK");
        code(status(token, second, 1), 200, "OK");
        jdbc.update(
                "UPDATE blog_article SET category_id=700,publish_time='2026-10-01 00:00:00.000', " +
                 "update_time='2026-10-01 00:00:00.000' WHERE article_id IN(?,?)",
                first, second);
        jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", first);
        jdbc.update("UPDATE blog_article SET summary='summary unique',is_top=1 WHERE article_id=?", second);
        for (String endpoint : List.of("/blog/articles", "/admin/articles")) {
            MvcResult found = mvc.perform(
                            req("GET", endpoint).header("Authorization", "Bearer " + token).param("keyword", "%_\\"))
                    .andReturn();
            code(found, 200, "OK");
            assertThat(data(found).get("total").asString()).isEqualTo("1");
            assertThat(data(found).get("records").get(0).get("articleId").asString()).isEqualTo(first);
            assertThat(data(call("GET", endpoint + "?keyword=summary", token, null)).get("total").asString()).isEqualTo(
                    "1");
            assertThat(data(call("GET", endpoint + "?keyword=Search", token, null)).get("total").asString()).isEqualTo(
                    "2");
        }
        assertThat(data(call("GET", "/blog/articles?categoryId=700&tagId=701&month=2026-10", null, null)).get("total")
                .asString()).isEqualTo("1");
        assertThat(data(call("GET", "/blog/articles?pageSize=1", null, null)).get("records").get(0).get("articleId")
                .asString()).isEqualTo(second);
        JsonNode beyond = data(call("GET", "/blog/articles?page=4&pageSize=1", null, null));
        assertThat(beyond.get("page").asInt()).isEqualTo(4);
        assertThat(beyond.get("records").isEmpty()).isTrue();
        assertThat(beyond.get("totalPages").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles?month=9999-12", null, null)).get("total").asString()).isEqualTo(
                "0");
        assertThat(data(call("GET", "/blog/articles?month=1000-01", null, null)).get("total").asString()).isEqualTo(
                "0");
    }

    @Test
    void validatesQueryAndPathBoundariesWithoutSilentCorrection() throws Exception {
        String token = actor("queries", 1);
        for (String query : List.of("page=0", "page=100001", "pageSize=101", "pageSize=0", "page=1.2", "categoryId=01",
                "tagId=9223372036854775808")) {
            for (String path : List.of("/blog/articles", "/admin/articles")) {
                code(call("GET", path + "?" + query, token, null), 400, "VALIDATION_ERROR");
            }
        }
        code(mvc.perform(req("GET", "/blog/articles").param("keyword", "  ")).andReturn(), 400, "VALIDATION_ERROR");
        for (String month : List.of("0999-01", "10000-01", "2026-13", "2026-00", "2026-1", "2026-02-01")) {
            code(call("GET", "/blog/articles?month=" + month, null, null), 400, "VALIDATION_ERROR");
        }
        for (String query : List.of("status=3", "visibility=0", "trash=2", "authorId=01")) {
            code(call("GET", "/admin/articles?" + query, token, null), 400, "VALIDATION_ERROR");
        }
        for (String query : List.of("period=week", "metric=comments", "limit=0", "limit=21", "limit=1.1")) {
            code(call("GET", "/blog/article-rankings?" + query, null, null), 400, "VALIDATION_ERROR");
        }
        code(call("GET", "/blog/archives?page=0", null, null), 400, "VALIDATION_ERROR");
        for (String id : List.of("01", "0", "-1", "1.0", "9223372036854775808")) {
            code(call("GET", "/admin/articles/" + id, token, null), 400, "VALIDATION_ERROR");
        }
        code(call("GET", "/admin/articles/9223372036854775807", token, null), 404, "NOT_FOUND");
    }

    @Test
    void archivesCountMonthsAndUseUtcHalfOpenRanges() throws Exception {
        String token = actor("archives", 1);
        String first = create(token, "Archive-A", "body");
        String second = create(token, "Archive-B", "body");
        String third = create(token, "Archive-C", "body");
        jdbc.update("UPDATE blog_article SET status=1,publish_time='2026-09-30 23:59:59.999' WHERE article_id=?",
                first);
        jdbc.update("UPDATE blog_article SET status=1,publish_time='2026-10-01 00:00:00.000' WHERE article_id=?",
                second);
        jdbc.update("UPDATE blog_article SET status=1,publish_time='2026-10-02 00:00:00.000' WHERE article_id=?",
                third);
        JsonNode archive = data(call("GET", "/blog/archives?pageSize=1", null, null));
        assertThat(archive.get("total").asString()).isEqualTo("2");
        assertThat(archive.get("totalPages").asString()).isEqualTo("2");
        assertThat(archive.get("records").get(0).get("month").asString()).isEqualTo("2026-10");
        assertThat(archive.get("records").get(0).get("articleCount").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles?month=2026-09", null, null)).get("total").asString()).isEqualTo(
                "1");
        assertThat(data(call("GET", "/blog/articles?month=2026-10", null, null)).get("total").asString()).isEqualTo(
                "2");
        assertThat(data(call("GET", "/blog/archives?page=3&pageSize=1", null, null)).get("records").isEmpty()).isTrue();
    }

    @Test
    void derivesReadingNavigationAndRelatedArticlesWithoutCountingCandidates() throws Exception {
        String token = actor("navigation", 1);
        seedDictionary();
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            String id = create(token, "Nav-" + index,
                    "# 标题\n\n你好 abc123 [link](https://target.test/ignored)\n\n```java\nignored code words\n```");
            ids.add(id);
            jdbc.update(
                    "UPDATE blog_article SET status=1,category_id=700,publish_time='2026-10-01 00:00:00.000' WHERE " +
                     "article_id=?",
                    id);
        }
        String version = revision(token, ids.get(4));
        MvcResult detail = call("GET", "/blog/articles/Nav-4", null, null);
        code(detail, 200, "OK");
        JsonNode value = data(detail);
        assertThat(value.get("wordCount").asString()).isEqualTo("6");
        assertThat(value.get("readingMinutes").asInt()).isEqualTo(1);
        assertThat(value.get("prevArticle").get("articleId").asString()).isEqualTo(ids.get(3));
        assertThat(value.get("nextArticle").get("articleId").asString()).isEqualTo(ids.get(5));
        assertThat(value.get("relatedArticles").size()).isEqualTo(6);
        assertThat(value.get("relatedArticles").get(0).get("articleId").asString()).isEqualTo(ids.get(8));
        assertThat(value.get("htmlContent").asString()).contains("<h1 id=", "<pre>", "noopener noreferrer");
        assertThat(value.get("viewCount").asString()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT SUM(view_count) FROM blog_article", Long.class)).isEqualTo(1);
        assertThat(revision(token, ids.get(4))).isEqualTo(version);
        jdbc.update("UPDATE blog_article SET visibility=2 WHERE article_id=?", ids.get(3));
        publicCaches.getCache("articleDetail").clear();
        assertThat(data(call("GET", "/blog/articles/Nav-4", null, null)).get("prevArticle").get("articleId")
                .asString()).isEqualTo(ids.get(2));
        jdbc.update("UPDATE blog_category SET status=0 WHERE category_id=700");
        publicCaches.getCache("articleDetail").clear(); publicCaches.getCache("categories").clear();
        assertThat(data(call("GET", "/blog/articles/Nav-4", null, null)).get("relatedArticles").isEmpty()).isTrue();
    }

    @Test
    void countsOnlyApprovedCommentsWithCompleteVisibleAncestryAndRealLikes() throws Exception {
        String token = actor("comments", 1);
        String id = create(token, "Comments", "body");
        code(status(token, id, 1), 200, "OK");
        long actor = Long.parseLong(userId("comments"));
        comment(1001, id, actor, null, 1);
        comment(1002, id, actor, 1001L, 3);
        comment(1003, id, actor, 1002L, 1);
        comment(1004, id, actor, null, 0);
        comment(1005, id, actor, 1004L, 1);
        comment(1006, id, actor, 1005L, 1);
        comment(1007, id, actor, null, 2);
        comment(1008, id, actor, 1007L, 1);
        // 占位链比 MySQL 默认递归深度还长，也得正确计入末尾通过的评论。
        for (int index = 2000; index <= 3010; index++) {
            comment(index, id, actor, index == 2000 ? null : (long) index - 1, index == 3010 ? 1 : 3);
        }
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", id, actor);
        assertThat(data(call("GET", "/blog/articles/Comments", null, null)).get("commentCount").asString()).isEqualTo(
                "3");
        JsonNode admin = data(call("GET", "/admin/articles/" + id, token, null));
        assertThat(admin.get("commentCount").asString()).isEqualTo("3");
        assertThat(admin.get("likeCount").asString()).isEqualTo("1");
        jdbc.update("UPDATE blog_comment SET status=2 WHERE comment_id=1001");
        assertThat(data(call("GET", "/blog/articles/Comments", null, null)).get("commentCount").asString()).isEqualTo(
                "1");
    }

    @Test
    void fourRankingsUseCumulativeMetricsAndStablePublicCandidatesWithoutReads() throws Exception {
        String token = actor("rank", 1);
        String old = create(token, "Rank-Old", "body");
        String recent = create(token, "Rank-New", "body");
        String tie = create(token, "Rank-Tie", "body");
        String privateId = create(token, "Rank-Private", "body");
        LocalDateTime month = LocalDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay();
        jdbc.update("UPDATE blog_article SET status=1,publish_time=?,view_count=900 WHERE article_id=?",
                month.minusDays(1), old);
        jdbc.update("UPDATE blog_article SET status=1,publish_time=?,view_count=50 WHERE article_id IN(?,?)", month,
                recent, tie);
        jdbc.update("UPDATE blog_article SET status=1,publish_time=?,visibility=2,view_count=9999 WHERE article_id=?",
                month, privateId);
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", recent, userId("rank"));
        assertThat(
                data(call("GET", "/blog/article-rankings", null, null)).get(0).get("articleId").asString()).isEqualTo(
                old);
        JsonNode view = data(call("GET", "/blog/article-rankings?period=month&metric=view", null, null));
        assertThat(view.size()).isEqualTo(2);
        assertThat(view.get(0).get("articleId").asString()).isEqualTo(tie);
        assertThat(view.get(0).get("hotValue").asString()).isEqualTo("50");
        for (String period : List.of("site", "month")) {
            JsonNode likes = data(
                    call("GET", "/blog/article-rankings?period=" + period + "&metric=like&limit=1", null, null));
            assertThat(likes.size()).isEqualTo(1);
            assertThat(likes.get(0).get("articleId").asString()).isEqualTo(recent);
            assertThat(likes.get(0).get("hotValue").asString()).isEqualTo("1");
        }
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                recent)).isEqualTo(50);
    }

    @Test
    void fileReferencesRequireCurrentContextOwnershipPermissionAndRealObject() throws Exception {
        String admin = actor("file_admin", 1);
        String author = actor("file_author", 2);
        String id = create(author, "Files", "body");
        String owner = userId("file_author");
        addFile(900, owner, "own.png");
        addFile(901, userId("file_admin"), "other.png");
        Map<String, Object> body = body("Files", "![image](" + context(id, "900") + ")");
        body.put("coverUrl", ORIGIN + context(id, "900"));
        MvcResult saved = write("PUT", "/admin/articles/" + id, author, revision(author, id), body);
        code(saved, 200, "OK");
        assertThat(data(saved).get("coverUrl").asString()).isEqualTo(context(id, "900"));
        assertThat(data(saved).get("htmlContent").asString()).contains(
                "/api/v1/admin/articles/" + id + "/files/900/content");
        assertThat(data(saved).get("markdownContent").asString()).contains("/blog/");
        code(status(author, id, 1), 200, "OK");
        assertThat(data(call("GET", "/blog/articles/Files", null, null)).get("htmlContent").asString()).contains(
                context(id, "900"));
        body.put("coverUrl", null);
        body.put("markdownContent", "![other](" + context(id, "901") + ")");
        code(write("PUT", "/admin/articles/" + id, author, revision(author, id), body), 400, "VALIDATION_ERROR");
        code(write("PUT", "/admin/articles/" + id, admin, revision(admin, id), body), 200, "OK");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=2 AND permission_id=60");
        code(status(author, id, 1), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(2,60)");
        body.put("markdownContent", "![missing](" + context(id, "999999") + ")");
        code(write("PUT", "/admin/articles/" + id, admin, revision(admin, id), body), 400, "VALIDATION_ERROR");
        Files.delete(files.getLocalRoot().resolve("other.png"));
        code(status(admin, id, 1), 503, "SERVICE_UNAVAILABLE");
        code(status(admin, id, 2), 200, "OK");
    }

    @Test
    void attachmentParsingRejectsCrossArticleEncodedAndNoncanonicalPathsAndNormalizesSiteHtml() throws Exception {
        String token = actor("file_paths", 1);
        String id = create(token, "Paths", "body");
        addFile(900, userId("file_paths"), "paths.png");
        String good = context(id, "900");
        for (String bad : List.of(context("99999", "900"), good + "?x=1", good + "#x",
                good.replace("/files/", "/%66iles/"),
                good.replace("/files/", "/%2566iles/"), good.replace("/files/", "/x/../files/"),
                good.replace("900/content", "0900/content"),
                "/api/v1/admin/articles/" + id + "/files/900/content", "/api/v1/admin/files/900/content",
                "/uploads/paths.png",
                ORIGIN + good.replace("/files/", "/%66iles/"), "//blog.example.test" + good)) {
            Map<String, Object> body = body("Paths", "[bad](" + bad + ")");
            code(write("PUT", "/admin/articles/" + id, token, revision(token, id), body), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body("Create-Path", "![x](" + good + ")"))),
                400, "VALIDATION_ERROR");
        Map<String, Object> raw = body("Paths",
                "<img src=\"" + ORIGIN + good + "\">\n\n```\n" + good + "\n```\n\n[external](https://external.test" + good + ")");
        MvcResult result = write("PUT", "/admin/articles/" + id, token, revision(token, id), raw);
        code(result, 200, "OK");
        assertThat(data(result).get("htmlContent").asString()).contains(
                "/api/v1/admin/articles/" + id + "/files/900/content", "https://external.test" + good, "<code>" + good);
        String version = revision(token, id);
        Files.delete(files.getLocalRoot().resolve("paths.png"));
        code(write("PUT", "/admin/articles/" + id, token, version,
                body("Paths", "only text " + good + "\n\n```\n" + good + "\n```")), 200, "OK");
    }

    @Test
    void concurrentAggregateWritesHaveOneWinnerAndReadsIncrementAtomically() throws Exception {
        String token = actor("concurrent", 1);
        String other = actor("concurrent_other", 1);
        String id = create(token, "Concurrent", "body");
        String version = revision(token, id);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return write("PUT", "/admin/articles/" + id, token, version, body("Concurrent", "first"));
            });
            var second = executor.submit(() -> {
                start.await();
                return write("PUT", "/admin/articles/" + id, other, version, body("Concurrent", "second"));
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus())).containsExactlyInAnyOrder(200, 412);
            code(status(token, id, 1), 200, "OK");
            List<Future<MvcResult>> reads = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                reads.add(executor.submit(() -> call("GET", "/blog/articles/Concurrent", null, null)));
            }
            for (var read : reads) {
                code(read.get(15, TimeUnit.SECONDS), 200, "OK");
            }
        }
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                id)).isEqualTo(12);
    }

    @Test
    void waitingArticleLockRechecksLatestBodyVersionBeforeMutation() throws Exception {
        String token = actor("waiting", 1);
        String id = create(token, "Waiting", "body");
        String version = revision(token, id);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var holder = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class,
                        id);
                locked.countDown();
                await(release);
                jdbc.update(
                        "UPDATE blog_article_content SET markdown_content='changed while waiting' WHERE article_id=?",
                        id);
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var waiting = executor.submit(
                    () -> write("PUT", "/admin/articles/" + id, token, version, body("Waiting", "overwrite")));
            awaitLockWait("blog_article");
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            code(waiting.get(10, TimeUnit.SECONDS), 412, "REVISION_CONFLICT");
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?",
                String.class, id)).isEqualTo("changed while waiting");
    }

    @Test
    void fileLockIsTakenBeforeArticleAndStaleReferenceSnapshotCannotCommit() throws Exception {
        String token = actor("file_lock", 1);
        String id = create(token, "File-Lock", "body");
        addFile(900, userId("file_lock"), "lock.png");
        String version = revision(token, id);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var holder = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                articles.lockFile(900);
                locked.countDown();
                await(release);
                jdbc.update("DELETE FROM blog_file WHERE file_id=900");
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = executor.submit(() -> write("PUT", "/admin/articles/" + id, token, version,
                    body("File-Lock", "![x](" + context(id, "900") + ")")));
            awaitLockWait("blog_file");
            // 写请求等文件锁时，另一事务还能拿文章锁，说明它没有先拿文章锁再等文件锁。
            new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET innodb_lock_wait_timeout=2");
                jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class,
                        id);
                return null;
            });
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            code(writer.get(10, TimeUnit.SECONDS), 400, "VALIDATION_ERROR");
        } finally {
            release.countDown();
        }
        assertThat(revision(token, id)).isEqualTo(version);
    }

    @Test
    void articlePermissionDictionaryCannotDeleteUsedCodesAndDoesNotExpandAdminGuard() throws Exception {
        String token = actor("fixed", 1);
        for (int permission : List.of(10, 11, 12, 13, 14, 15, 60)) {
            MvcResult detail = call("GET", "/admin/permissions/" + permission, token, null);
            code(detail, 200, "OK");
            code(write("DELETE", "/admin/permissions/" + permission, token,
                    detail.getResponse().getHeader("X-Revision"), null), 409, "STATE_CONFLICT");
        }
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).doesNotContain("blog:article:query");
    }

    @Test
    void waitingArticleLockRevalidatesPermissionAndOriginalToken() throws Exception {
        for (String mode : List.of("permission", "token")) {
            String username = "waiting_" + mode;
            String token = actor(username, 1);
            String id = create(token, "Wait-" + mode, "body");
            String version = revision(token, id);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var holder = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                    jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class,
                            id);
                    locked.countDown();
                    await(release);
                    if ("permission".equals(mode)) {
                        // 这里直接改隔离测试数据来检查锁后重查，生产授权写入仍要遵守用户锁顺序。
                        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=12");
                    }
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var writer = executor.submit(
                        () -> write("PUT", "/admin/articles/" + id, token, version, body("Wait-" + mode, "overwrite")));
                awaitLockWait("blog_article");
                if ("token".equals(mode)) {
                    sessions.revokeAll(Long.parseLong(userId(username)));
                }
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                code(writer.get(10, TimeUnit.SECONDS), "permission".equals(mode) ? 403 : 401,
                        "permission".equals(mode) ? "FORBIDDEN" : "UNAUTHENTICATED");
            } finally {
                release.countDown();
                jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=12");
            }
            assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?",
                    String.class, id)).isEqualTo("body");
        }
    }

    @Test
    void realLockTimeoutReturns503AndRollsBackWholeWrite() throws Exception {
        String token = actor("timeout", 1);
        String id = create(token, "Timeout", "body");
        String version = revision(token, id);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var holder = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class,
                        id);
                locked.countDown();
                await(release);
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");
                try {
                    return write("PUT", "/admin/articles/" + id, token, version, body("Timeout", "overwrite"));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                } finally {
                    status.setRollbackOnly();
                    jdbc.execute("SET SESSION innodb_lock_wait_timeout=50");
                }
            }));
            code(writer.get(8, TimeUnit.SECONDS), 503, "SERVICE_UNAVAILABLE");
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(revision(token, id)).isEqualTo(version);
    }

    @Test
    void successfulAuditFailurePreventsArticleBodyAndTagsCommit() throws Exception {
        String token = actor("audit_failure", 1);
        seedDictionary();
        String id = create(token, "Audit", "body");
        String version = revision(token, id);
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN" +
                 " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        Map<String, Object> body = body("Audit", "changed");
        body.put("tagIds", List.of("701"));
        code(write("PUT", "/admin/articles/" + id, token, version, body), 503, "SERVICE_UNAVAILABLE");
        assertThat(revision(token, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_tag WHERE article_id=?", Integer.class,
                id)).isZero();
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?",
                String.class, id)).isEqualTo("body");
    }

    @Test
    void deletingAssociatedDictionaryChangesAggregateRevisionWithoutDestroyingContent() throws Exception {
        String token = actor("dictionary_revision", 1);
        seedDictionary();
        Map<String, Object> body = body("Dictionary", "body");
        body.put("categoryId", "700");
        body.put("tagIds", List.of("701", "702"));
        MvcResult created = call("POST", "/admin/articles", token, json.writeValueAsString(body));
        code(created, 201, "OK");
        String id = data(created).get("articleId").asString();
        String version = revision(token, id);
        jdbc.update("DELETE FROM blog_tag WHERE tag_id=701");
        assertThat(revision(token, id)).isNotEqualTo(version);
        code(write("PUT", "/admin/articles/" + id + "/status", token, version, Map.of("status", 1)), 412,
                "REVISION_CONFLICT");
        String tagVersion = revision(token, id);
        jdbc.update("DELETE FROM blog_category WHERE category_id=700");
        assertThat(revision(token, id)).isNotEqualTo(tagVersion);
        JsonNode value = data(call("GET", "/admin/articles/" + id, token, null));
        assertThat(value.get("categoryId").isNull()).isTrue();
        assertThat(value.get("tagIds").toString()).isEqualTo("[\"702\"]");
        assertThat(value.get("markdownContent").asString()).isEqualTo("body");
    }

    @Test
    void categoryCrudUsesDefaultsFullReplacementVersionsAndPublicFields() throws Exception {
        String token = actor("category_crud", 1);
        MvcResult created = call("POST", "/admin/categories", token,
                "{\"categoryName\":\" 技术 🌿 \",\"slug\":\" Tech \"}");
        code(created, 201, "OK");
        String id = data(created).get("categoryId").asString();
        String path = "/admin/categories/" + id;
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1" + path);
        assertThat(data(created).get("categoryName").asString()).isEqualTo("技术 🌿");
        assertThat(data(created).get("description").isNull()).isTrue();
        assertThat(data(created).get("sortOrder").asInt()).isZero();
        assertThat(data(created).get("status").asInt()).isEqualTo(1);
        assertThat(data(created).get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(created.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        MvcResult detail = call("GET", path, token, null);
        code(detail, 200, "OK");
        String version = detail.getResponse().getHeader("X-Revision");
        assertThat(version).matches("\"[0-9a-f]{64}\"");
        jdbc.update("UPDATE blog_category SET update_time='2020-01-01' WHERE category_id=?", id);
        assertThat(dictionaryRevision(token, path)).isEqualTo(version);
        Map<String, Object> body = categoryBody("技术 🌿", "Tech", null, 0, 1);
        MvcResult same = write("PUT", path, token, version, body);
        code(same, 200, "OK");
        assertThat(same.getResponse().getHeader("X-Revision")).isEqualTo(version);
        body.put("description", "  分类说明  ");
        body.put("sortOrder", -5);
        MvcResult updated = write("PUT", path, token, version, body);
        code(updated, 200, "OK");
        assertThat(data(updated).get("description").asString()).isEqualTo("分类说明");
        assertThat(updated.getResponse().getHeader("X-Revision")).isNotEqualTo(version);
        code(write("DELETE", path, token, version, null), 412, "REVISION_CONFLICT");
        JsonNode publicRow = data(call("GET", "/blog/categories", null, null)).get("records").get(0);
        assertThat(publicRow.size()).isEqualTo(5);
        assertThat(publicRow.get("status")).isNull();
        assertThat(publicRow.get("createTime")).isNull();
        body.put("description", "  ");
        MvcResult cleared = write("PUT", path, token, dictionaryRevision(token, path), body);
        code(cleared, 200, "OK");
        assertThat(data(cleared).get("description").isNull()).isTrue();
        MvcResult deleted = write("DELETE", path, token, dictionaryRevision(token, path), null);
        code(deleted, 200, "OK");
        assertThat(data(deleted).isNull()).isTrue();
        assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        code(call("GET", path, token, null), 404, "NOT_FOUND");
        code(write("DELETE", path, token, version, null), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject(
                "SELECT target_id FROM sys_operation_log WHERE operation_target='blog:category' AND " +
                 "operation_type='create' AND result=1",
                String.class)).isEqualTo(id);
    }

    @Test
    void tagCrudReturnsOnlyPublicFieldsAndVersionsOnlyBusinessData() throws Exception {
        String token = actor("tag_crud", 1);
        MvcResult created = call("POST", "/admin/tags", token, "{\"tagName\":\" Java 🌿 \",\"slug\":\" Java \"}");
        code(created, 201, "OK");
        String id = data(created).get("tagId").asString();
        String path = "/admin/tags/" + id;
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1" + path);
        assertThat(created.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(created).get("tagName").asString()).isEqualTo("Java 🌿");
        String version = dictionaryRevision(token, path);
        jdbc.update("UPDATE blog_tag SET update_time='2020-01-01' WHERE tag_id=?", id);
        assertThat(dictionaryRevision(token, path)).isEqualTo(version);
        MvcResult same = write("PUT", path, token, version, Map.of("tagName", "Java 🌿", "slug", "Java"));
        code(same, 200, "OK");
        assertThat(same.getResponse().getHeader("X-Revision")).isEqualTo(version);
        MvcResult saved = write("PUT", path, token, version, Map.of("tagName", "新标签", "slug", "java"));
        code(saved, 200, "OK");
        assertThat(saved.getResponse().getHeader("X-Revision")).isNotEqualTo(version);
        MvcResult list = call("GET", "/blog/tags", null, null);
        code(list, 200, "OK");
        JsonNode row = data(list).get("records").get(0);
        assertThat(row.size()).isEqualTo(3);
        assertThat(row.get("tagName").asString()).isEqualTo("新标签");
        assertThat(row.get("createTime")).isNull();
        code(write("PUT", path, token, version, Map.of("tagName", "旧标签", "slug", "old")), 412, "REVISION_CONFLICT");
        code(write("DELETE", path, token, dictionaryRevision(token, path), null), 200, "OK");
        code(call("GET", path, token, null), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject(
                "SELECT target_id FROM sys_operation_log WHERE operation_target='blog:tag' AND " +
                 "operation_type='create' AND result=1",
                String.class)).isEqualTo(id);
    }

    @Test
    void dictionaryPagesUseLiteralNamesStableOrderStatusAndMatchingTotals() throws Exception {
        String token = actor("dictionary_pages", 1);
        jdbc.update(
                "INSERT INTO blog_category(category_id,category_name,slug,sort_order,status) VALUES(710,'Z%_','z',-1," +
                 "1),(711,'A%_','a',-1,1),(712,'隐藏','hidden',-2,0),(713,'空分类','empty',0,1)");
        jdbc.update(
                "INSERT INTO blog_tag(tag_id,tag_name,slug) VALUES(710,'Z%_','z'),(711,'A%_','a'),(712,'空标签','empty')");
        MvcResult categories = call("GET", "/blog/categories?pageSize=1", null, null);
        code(categories, 200, "OK");
        assertThat(categories.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(categories).get("total").asString()).isEqualTo("3");
        assertThat(data(categories).get("totalPages").asString()).isEqualTo("3");
        assertThat(data(categories).get("records").get(0).get("categoryId").asString()).isEqualTo("710");
        assertThat(data(call("GET", "/blog/categories?page=2&pageSize=1", null, null)).get("records").get(0)
                .get("categoryId").asString()).isEqualTo("711");
        MvcResult filtered = mvc.perform(
                        req("GET", "/admin/categories").header("Authorization", "Bearer " + token).param("keyword",
                        " %_ "))
                .andReturn();
        code(filtered, 200, "OK");
        assertThat(data(filtered).get("total").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/admin/categories?status=0", token, null)).get("total").asString()).isEqualTo("1");
        MvcResult tags = mvc.perform(
                req("GET", "/admin/tags").header("Authorization", "Bearer " + token).param("keyword", "%_")
                        .param("pageSize", "1")).andReturn();
        code(tags, 200, "OK");
        assertThat(data(tags).get("total").asString()).isEqualTo("2");
        assertThat(data(tags).get("records").get(0).get("tagId").asString()).isEqualTo("710");
        assertThat(data(call("GET", "/blog/tags", null, null)).get("total").asString()).isEqualTo("3");
        JsonNode empty = data(call("GET", "/admin/tags?page=100", token, null));
        assertThat(empty.get("page").asInt()).isEqualTo(100);
        assertThat(empty.get("records").isEmpty()).isTrue();
        jdbc.update("DELETE FROM blog_tag");
        publicCaches.getCache("tags").clear();
        JsonNode zero = data(call("GET", "/blog/tags", null, null));
        assertThat(zero.get("total").asString()).isEqualTo("0");
        assertThat(zero.get("totalPages").asString()).isEqualTo("0");
        for (String path : List.of("/blog/categories?page=0", "/blog/tags?pageSize=101", "/admin/categories?status=2",
                "/admin/categories?page=100001")) {
            code(call("GET", path, token, null), 400, "VALIDATION_ERROR");
        }
        code(mvc.perform(req("GET", "/admin/tags").header("Authorization", "Bearer " + token)
                .param("keyword", "  ")).andReturn(), 400, "VALIDATION_ERROR");
    }

    @Test
    void dictionaryBindingRejectsMissingNullUnknownDuplicateAndWrongTypes() throws Exception {
        String token = actor("dictionary_binding", 1);
        seedDictionary();
        for (String kind : List.of("categories", "tags")) {
            String path = "/admin/" + kind;
            boolean category = kind.equals("categories");
            Map<String, Object> valid = category ? categoryBody("分类", "category", null, 0, 1)
                    : new LinkedHashMap<>(Map.of("tagName", "标签一", "slug", "tag-one"));
            String target = path + (category ? "/700" : "/701");
            String revision = dictionaryRevision(token, target);
            for (String field : valid.keySet()) {
                Map<String, Object> missing = new LinkedHashMap<>(valid);
                missing.remove(field);
                code(write("PUT", target, token, revision, missing), 400, "VALIDATION_ERROR");
            }
            for (String extra : List.of("statusUnknown", "createTime", "updateTime", "authorId", "completeReplace",
                    "descriptionPresent", category ? "categoryId" : "tagId")) {
                Map<String, Object> body = new LinkedHashMap<>(valid);
                body.put(extra, "1");
                code(call("POST", path, token, json.writeValueAsString(body)), 400, "VALIDATION_ERROR");
            }
            String name = category ? "categoryName" : "tagName";
            for (String bad : List.of("null", "12", "true", "\"  \"", json.writeValueAsString("🌿".repeat(65)))) {
                code(call("POST", path, token, "{\"" + name + "\":" + bad + ",\"slug\":\"valid\"}"), 400,
                        "VALIDATION_ERROR");
            }
            for (String slug : List.of("null", "1", "\"a--b\"", "\"中文\"", json.writeValueAsString("a".repeat(101)))) {
                code(call("POST", path, token, "{\"" + name + "\":\"新名称\",\"slug\":" + slug + "}"), 400,
                        "VALIDATION_ERROR");
            }
            code(call("POST", path, token, "{\"" + name + "\":\"a\",\"" + name + "\":\"b\",\"slug\":\"dup\"}"), 400,
                    "VALIDATION_ERROR");
            for (String invalid : List.of("01", "0", "-1", "9223372036854775808")) {
                code(call("GET", path + "/" + invalid, token, null), 400, "VALIDATION_ERROR");
            }
            code(write("PUT", target, token, null, valid), 428, "PRECONDITION_REQUIRED");
            for (String bad : List.of("*", "plain", revision + ", " + revision)) {
                code(write("PUT", target, token, bad, valid), 400, "VALIDATION_ERROR");
                code(write("DELETE", target, token, bad, null), 400, "VALIDATION_ERROR");
            }
            code(write("DELETE", target, token, null, null), 428, "PRECONDITION_REQUIRED");
            code(write("DELETE", target, token, revision, Map.of("authorId", "1")), 400, "VALIDATION_ERROR");
            valid.put(name, "🌿".repeat(64));
            valid.put("slug", "unicode-" + kind);
            code(call("POST", path, token, json.writeValueAsString(valid)), 201, "OK");
        }
        for (String value : List.of("null", "\"1\"", "1.5", "true", "2147483648")) {
            code(call("POST", "/admin/categories", token,
                            "{\"categoryName\":\"new\",\"slug\":\"new\",\"sortOrder\":" + value + "}"), 400,
                    "VALIDATION_ERROR");
        }
        for (String value : List.of("null", "\"1\"", "2", "true")) {
            code(call("POST", "/admin/categories", token,
                    "{\"categoryName\":\"new\",\"slug\":\"new\",\"status\":" + value + "}"), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/categories", token,
                json.writeValueAsString(categoryBody("new", "new", "🌿".repeat(256), 0, 1))), 400, "VALIDATION_ERROR");
    }

    @Test
    void dictionaryUniquenessUsesDatabaseCollationAndCaseSensitiveSlugs() throws Exception {
        String token = actor("dictionary_unique", 1);
        for (String kind : List.of("categories", "tags")) {
            String path = "/admin/" + kind;
            String name = kind.equals("categories") ? "categoryName" : "tagName";
            MvcResult first = call("POST", path, token, json.writeValueAsString(Map.of(name, "Café", "slug", "Case")));
            code(first, 201, "OK");
            code(call("POST", path, token, json.writeValueAsString(Map.of(name, "cafe", "slug", "another"))), 409,
                    "UNIQUE_CONFLICT");
            code(call("POST", path, token, json.writeValueAsString(Map.of(name, "另一个", "slug", "Case"))), 409,
                    "UNIQUE_CONFLICT");
            MvcResult second = call("POST", path, token,
                    json.writeValueAsString(Map.of(name, "另一个", "slug", "case")));
            code(second, 201, "OK");
            String id = data(second).get(kind.equals("categories") ? "categoryId" : "tagId").asString();
            String target = path + "/" + id;
            String version = dictionaryRevision(token, target);
            Object duplicate = kind.equals("categories") ? categoryBody("cafe", "case", null, 0, 1) : Map.of(name,
                    "cafe", "slug", "case");
            code(write("PUT", target, token, version, duplicate), 409, "UNIQUE_CONFLICT");
            assertThat(dictionaryRevision(token, target)).isEqualTo(version);
        }
    }

    @Test
    void taxonomyPermissionsAreIndependentGlobalAndCannotBeDeleted() throws Exception {
        String admin = actor("taxonomy_admin", 1);
        String author = actor("taxonomy_author", 2);
        String member = actor("taxonomy_member", 3);
        seedDictionary();
        for (String path : List.of("/admin/categories", "/admin/tags")) {
            code(call("GET", path, null, null), 401, "UNAUTHENTICATED");
            code(call("GET", path, member, null), 403, "FORBIDDEN");
            code(call("GET", path, author, null), 200, "OK");
            Object body = path.endsWith("categories") ? categoryBody("新分类", "new", null, 0, 1) : Map.of("tagName",
                    "新标签", "slug", "new");
            String target = path + (path.endsWith("categories") ? "/700" : "/701");
            code(call("GET", target, author, null), 200, "OK");
            code(write("POST", path, author, null, body), 403, "FORBIDDEN");
            code(write("PUT", target, author, null, body), 403, "FORBIDDEN");
            code(write("DELETE", target, author, null, null), 403, "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(4,'taxonomy_self','字典维护','SELF')");
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 4,permission_id FROM sys_permission " +
                 "WHERE permission_id IN(1,20,21,22,23,30,31,32,33)");
        String self = actor("taxonomy_self", 4);
        code(write("PUT", "/admin/categories/700", self, dictionaryRevision(self, "/admin/categories/700"),
                categoryBody("SELF分类", "self-category", null, 0, 1)), 200, "OK");
        code(write("PUT", "/admin/tags/701", self, dictionaryRevision(self, "/admin/tags/701"),
                Map.of("tagName", "SELF标签", "slug", "self-tag")), 200, "OK");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=4 AND permission_id=1");
        code(call("GET", "/admin/categories", self, null), 403, "FORBIDDEN");
        for (int id : List.of(20, 21, 22, 23, 30, 31, 32, 33)) {
            String path = "/admin/permissions/" + id;
            code(write("DELETE", path, admin, dictionaryRevision(admin, path), null), 409, "STATE_CONFLICT");
        }
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).noneMatch(permission -> permission.startsWith("blog:"));
    }

    @Test
    void dictionaryHttpChangesImmediatelyReachArticleViewsFiltersAndAggregateVersions() throws Exception {
        String token = actor("dictionary_article", 1);
        MvcResult category = call("POST", "/admin/categories", token,
                "{\"categoryName\":\"公开分类\",\"slug\":\"public-category\"}");
        code(category, 201, "OK");
        String categoryId = data(category).get("categoryId").asString();
        MvcResult tag = call("POST", "/admin/tags", token, "{\"tagName\":\"旧标签\",\"slug\":\"old-tag\"}");
        code(tag, 201, "OK");
        String tagId = data(tag).get("tagId").asString();
        Map<String, Object> body = body("Linked", "保留正文");
        body.put("categoryId", categoryId);
        body.put("tagIds", List.of(tagId));
        MvcResult created = write("POST", "/admin/articles", token, null, body);
        code(created, 201, "OK");
        String id = data(created).get("articleId").asString();
        code(status(token, id, 1), 200, "OK");
        String articleVersion = revision(token, id);
        String categoryPath = "/admin/categories/" + categoryId;
        code(write("PUT", categoryPath, token, dictionaryRevision(token, categoryPath),
                categoryBody("改名分类", "renamed-category", null, 0, 0)), 200, "OK");
        String tagPath = "/admin/tags/" + tagId;
        code(write("PUT", tagPath, token, dictionaryRevision(token, tagPath),
                Map.of("tagName", "新标签", "slug", "new-tag")), 200, "OK");
        JsonNode publicArticle = data(call("GET", "/blog/articles/Linked", null, null));
        assertThat(publicArticle.get("category").isNull()).isTrue();
        assertThat(publicArticle.get("tags").get(0).get("tagName").asString()).isEqualTo("新标签");
        assertThat(data(call("GET", "/blog/articles?categoryId=" + categoryId, null, null)).get("total")
                .asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/articles?tagId=" + tagId, null, null)).get("total").asString()).isEqualTo(
                "1");
        JsonNode adminArticle = data(call("GET", "/admin/articles/" + id, token, null));
        assertThat(adminArticle.get("category").get("categoryName").asString()).isEqualTo("改名分类");
        assertThat(adminArticle.get("category").get("status").asInt()).isZero();
        assertThat(revision(token, id)).isEqualTo(articleVersion);
        code(write("PUT", "/admin/articles/" + id, token, articleVersion, body), 200, "OK");
        code(write("PUT", categoryPath, token, dictionaryRevision(token, categoryPath),
                categoryBody("改名分类", "renamed-category", null, 0, 1)), 200, "OK");
        assertThat(data(call("GET", "/blog/articles?categoryId=" + categoryId, null, null)).get("total")
                .asString()).isEqualTo("1");
        code(write("DELETE", tagPath, token, dictionaryRevision(token, tagPath), null), 200, "OK");
        assertThat(revision(token, id)).isNotEqualTo(articleVersion);
        code(write("PUT", "/admin/articles/" + id, token, articleVersion, body), 412, "REVISION_CONFLICT");
        code(write("PUT", "/admin/articles/" + id, token, revision(token, id), body), 400, "VALIDATION_ERROR");
        String tagDeletedVersion = revision(token, id);
        code(write("DELETE", categoryPath, token, dictionaryRevision(token, categoryPath), null), 200, "OK");
        assertThat(revision(token, id)).isNotEqualTo(tagDeletedVersion);
        body.put("tagIds", List.of());
        code(write("PUT", "/admin/articles/" + id, token, revision(token, id), body), 400, "VALIDATION_ERROR");
        JsonNode remaining = data(call("GET", "/admin/articles/" + id, token, null));
        assertThat(remaining.get("categoryId").isNull()).isTrue();
        assertThat(remaining.get("tagIds").isEmpty()).isTrue();
        assertThat(remaining.get("markdownContent").asString()).isEqualTo("保留正文");
        assertThat(remaining.get("status").asInt()).isEqualTo(1);
        assertThat(data(call("GET", "/blog/articles?tagId=" + tagId, null, null)).get("total").asString()).isEqualTo(
                "0");
    }

    @Test
    void dictionaryAuditFailureRollsBackCreationUpdatesAndCascadingDeletion() throws Exception {
        String token = actor("dictionary_rollback", 1);
        seedDictionary();
        Map<String, Object> body = body("Dictionary-Rollback", "保留");
        body.put("categoryId", "700");
        body.put("tagIds", List.of("701"));
        MvcResult created = write("POST", "/admin/articles", token, null, body);
        code(created, 201, "OK");
        String id = data(created).get("articleId").asString();
        String original = revision(token, id);
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN" +
                 " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        for (String kind : List.of("categories", "tags")) {
            String path = "/admin/" + kind;
            String target = path + (kind.equals("categories") ? "/700" : "/701");
            String version = dictionaryRevision(token, target);
            Object next = kind.equals("categories") ? categoryBody("新分类", "new-category", "说明", 1, 0) : Map.of(
                    "tagName", "新标签", "slug", "new-tag");
            code(write("POST", path, token, null, next), 503, "SERVICE_UNAVAILABLE");
            code(write("PUT", target, token, version, next), 503, "SERVICE_UNAVAILABLE");
            assertThat(dictionaryRevision(token, target)).isEqualTo(version);
            code(write("DELETE", target, token, version, null), 503, "SERVICE_UNAVAILABLE");
            assertThat(dictionaryRevision(token, target)).isEqualTo(version);
            assertThat(revision(token, id)).isEqualTo(original);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_category", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_tag", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_tag WHERE article_id=?", Integer.class,
                id)).isEqualTo(1);
    }

    @Test
    void dictionaryWritesCheckLatestVersionAfterWaitingForTheirRowLock() throws Exception {
        String token = actor("dictionary_wait", 1);
        seedDictionary();
        for (String kind : List.of("category", "tag")) {
            boolean category = kind.equals("category");
            String path = category ? "/admin/categories/700" : "/admin/tags/701";
            String version = dictionaryRevision(token, path);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> holder = pool.submit(
                        () -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                            jdbc.queryForList("SELECT * FROM blog_" + kind + " WHERE " + kind + "_id=? FOR UPDATE",
                                    category ? 700 : 701);
                            locked.countDown();
                            await(release);
                            jdbc.update(
                                    "UPDATE blog_" + kind + " SET " + kind + "_name='锁内已修改' WHERE " + kind + "_id=?",
                                    category ? 700 : 701);
                        }));
                await(locked);
                Future<MvcResult> writer = pool.submit(() -> write("DELETE", path, token, version, null));
                try {
                    awaitLockWait("blog_" + kind);
                } finally {
                    release.countDown();
                }
                holder.get(10, TimeUnit.SECONDS);
                code(writer.get(10, TimeUnit.SECONDS), 412, "REVISION_CONFLICT");
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void dictionaryDeleteWaitsForArticleAssociationAndRemovesNewlyCommittedReferences() throws Exception {
        String token = actor("dictionary_delete_wait", 1);
        String author = actor("dictionary_delete_author", 2);
        seedDictionary();
        String id = create(author, "Association-Wait", "保留正文");
        for (String kind : List.of("category", "tag")) {
            boolean category = kind.equals("category");
            String path = category ? "/admin/categories/700" : "/admin/tags/701";
            String version = dictionaryRevision(token, path);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> holder = pool.submit(
                        () -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                            jdbc.queryForList("SELECT * FROM blog_" + kind + " WHERE " + kind + "_id=? FOR SHARE",
                                    category ? 700 : 701);
                            if (category) {
                                jdbc.update("UPDATE blog_article SET category_id=700 WHERE article_id=?", id);
                            } else {
                                jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", id);
                            }
                            locked.countDown();
                            await(release);
                        }));
                await(locked);
                Future<MvcResult> deletion = pool.submit(() -> write("DELETE", path, token, version, null));
                try {
                    awaitLockWait("blog_" + kind);
                } finally {
                    release.countDown();
                }
                holder.get(10, TimeUnit.SECONDS);
                code(deletion.get(10, TimeUnit.SECONDS), 200, "OK");
            } finally {
                release.countDown();
            }
        }
        JsonNode value = data(call("GET", "/admin/articles/" + id, author, null));
        assertThat(value.get("categoryId").isNull()).isTrue();
        assertThat(value.get("tagIds").isEmpty()).isTrue();
        assertThat(value.get("markdownContent").asString()).isEqualTo("保留正文");
    }

    @Test
    void dictionaryWritesRecheckPermissionAfterWaitingForTheirRowLock() throws Exception {
        String token = actor("dictionary_revoke_wait", 1);
        seedDictionary();
        for (String kind : List.of("category", "tag")) {
            boolean category = kind.equals("category");
            String path = category ? "/admin/categories/700" : "/admin/tags/701";
            String version = dictionaryRevision(token, path);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> holder = pool.submit(
                        () -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                            jdbc.queryForList("SELECT * FROM blog_" + kind + " WHERE " + kind + "_id=? FOR UPDATE",
                                    category ? 700 : 701);
                            locked.countDown();
                            await(release);
                        }));
                await(locked);
                Future<MvcResult> deletion = pool.submit(() -> write("DELETE", path, token, version, null));
                try {
                    awaitLockWait("blog_" + kind);
                    // 这里只改隔离测试数据，生产撤权仍先锁操作者，不能绕过用户锁。
                    jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code=?",
                            "blog:" + kind + ":delete");
                } finally {
                    release.countDown();
                }
                holder.get(10, TimeUnit.SECONDS);
                code(deletion.get(10, TimeUnit.SECONDS), 403, "FORBIDDEN");
                assertThat(dictionaryRevision(token, path)).isEqualTo(version);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void commentCreationUsesSessionPolicyAndPreservesPlainTextWithoutHtml() throws Exception {
        String admin = actor("comment_admin", 1);
        String member = actor("comment_member", 3);
        String article = published(admin, "Comment-plain");
        String plain = "<script>alert('x')</script> **文字** & <img src=x onerror=bad()>";
        MvcResult created = postComment(member, article, "  " + plain + "  ", null);
        code(created, 201, "OK");
        JsonNode value = data(created);
        assertThat(value.get("content").asString()).isEqualTo(plain);
        assertThat(value.get("userId").asString()).isEqualTo(userId("comment_member"));
        assertThat(value.get("status").asInt()).isZero();
        assertThat(value.has("htmlContent")).isFalse();
        assertThat(value.has("author")).isFalse();
        assertThat(created.getResponse().getHeader("Location")).isNull();
        assertThat(value.get("createTime").asString()).matches(".*\\.\\d{3}Z");
        code(postComment(admin, article, "管理员也要审核", null), 201, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE status=0", Integer.class)).isEqualTo(2);
        commentPolicy(true, false);
        MvcResult approved = postComment(member, article, "直接通过", null);
        code(approved, 201, "OK");
        assertThat(data(approved).get("status").asInt()).isEqualTo(1);
        JsonNode visible = data(call("GET", commentsPath(article), admin, null));
        assertThat(visible.get("total").asString()).isEqualTo("1");
        JsonNode publicComment = visible.get("records").get(0);
        assertThat(publicComment.has("userId")).isFalse();
        assertThat(publicComment.get("author").propertyNames()).containsExactlyInAnyOrder("userId", "nickname",
                "avatar");
    }

    @Test
    void disabledCommentSwitchesBlockNewWritesButKeepHistoryAndLikes() throws Exception {
        String token = actor("comment_switches", 1);
        String article = published(token, "Switches");
        commentPolicy(true, false);
        code(postComment(token, article, "保留历史", null), 201, "OK");
        commentPolicy(false, true);
        code(postComment(token, article, "站点关闭", null), 409, "STATE_CONFLICT");
        commentPolicy(true, false);
        jdbc.update("UPDATE blog_article SET allow_comment=0 WHERE article_id=?", article);
        code(postComment(token, article, "文章关闭", null), 409, "STATE_CONFLICT");
        assertThat(data(call("GET", commentsPath(article), null, null)).get("total").asString()).isEqualTo("1");
        code(call("PUT", likePath(article), token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(1);
    }

    @Test
    void malformedOrMissingCommentPolicyNeverFallsBackToOpen() throws Exception {
        String token = actor("comment_config", 1);
        String article = published(token, "Policy");
        for (String config : List.of("{}", "[]", "{\"enabled\":true}",
                "{\"enabled\":1,\"reviewRequired\":false}",
                "{\"enabled\":true,\"reviewRequired\":null}",
                "{\"enabled\":true,\"reviewRequired\":false,\"extra\":1}",
                json.writeValueAsString(Map.of("enabled", true, "reviewRequired", "x".repeat(17000))))) {
            jdbc.update("UPDATE blog_site_config SET config_value=? WHERE config_key='comment'", config);
            code(postComment(token, article, "不能放行", null), 503, "SERVICE_UNAVAILABLE");
        }
        commentPolicy(true, false);
        jdbc.update("UPDATE blog_site_config SET is_public=1 WHERE config_key='comment'");
        code(postComment(token, article, "不能公开审核策略", null), 503, "SERVICE_UNAVAILABLE");
        jdbc.update("DELETE FROM blog_site_config WHERE config_key='comment'");
        code(postComment(token, article, "缺配置", null), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isZero();
    }

    @Test
    void everyPublicInteractionRejectsAllNonPublicArticleStatesEvenForAdmin() throws Exception {
        String token = actor("interaction_public", 1);
        String article = published(token, "Public-only");
        String id = commentId(postComment(token, article, "本人历史", null));
        code(call("PUT", likePath(article), token, null), 200, "OK");
        for (String state : List.of("status=0", "status=2", "visibility=2", "deleted_at=UTC_TIMESTAMP(3)",
                "status=0,publish_time=NULL", "publish_time=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY)")) {
            jdbc.update(
                    "UPDATE blog_article SET status=1,visibility=1,deleted_at=NULL,publish_time=DATE_SUB" +
                     "(UTC_TIMESTAMP(3),INTERVAL 1 DAY) WHERE article_id=?",
                    article);
            jdbc.update("UPDATE blog_article SET " + state + " WHERE article_id=?", article);
            code(call("GET", commentsPath(article), null, null), 404, "NOT_FOUND");
            code(postComment(token, article, "不能写", null), 404, "NOT_FOUND");
            for (String method : List.of("GET", "PUT", "DELETE")) {
                code(call(method, likePath(article), token, null), 404, "NOT_FOUND");
            }
            assertThat(data(call("GET", "/me/comments?articleId=" + article, token, null)).get("total")
                    .asString()).isEqualTo("1");
            code(call("GET", "/admin/comments/" + id, token, null), 200, "OK");
        }
        code(review(token, id, 1), 200, "OK");
        code(call("DELETE", "/me/comments/" + id, token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like", Integer.class)).isEqualTo(1);
        assertThat(
                data(call("GET", "/me/comments", token, null)).get("records").get(0).has("markdownContent")).isFalse();
    }

    @Test
    void commentJsonStrictlyRejectsIdentityStatusTypesDuplicatesAndUnicodeOverflow() throws Exception {
        String token = actor("comment_strict", 1);
        String article = published(token, "Comment-strict");
        for (String body : List.of("{}", "{\"content\":null}", "{\"content\":1}", "{\"content\":true}",
                "{\"content\":\"  \"}", "{\"content\":\"a\",\"content\":\"b\"}",
                "{\"content\":\"a\",\"parentId\":1}", "{\"content\":\"a\",\"parentId\":\"01\"}",
                "{\"content\":\"a\",\"parentId\":\"9223372036854775808\"}",
                json.writeValueAsString(Map.of("content", "🌿".repeat(2001))))) {
            code(call("POST", commentsPath(article), token, body), 400, "VALIDATION_ERROR");
        }
        for (String key : List.of("articleId", "userId", "authorId", "status", "createTime", "htmlContent")) {
            code(call("POST", commentsPath(article), token, json.writeValueAsString(Map.of("content", "a", key, "1"))),
                    400, "VALIDATION_ERROR");
        }
        code(postComment(token, article, "🌿".repeat(2000), null), 201, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(1);
    }

    @Test
    void interactionPathsQueriesAndEmptyBodyRulesAreStrict() throws Exception {
        String token = actor("interaction_parameters", 1);
        String article = published(token, "Parameters");
        for (String id : List.of("0", "01", "-1", "1.2", "9223372036854775808")) {
            code(call("GET", commentsPath(id), null, null), 400, "VALIDATION_ERROR");
            code(call("GET", likePath(id), token, null), 400, "VALIDATION_ERROR");
            code(call("DELETE", "/me/comments/" + id, token, null), 400, "VALIDATION_ERROR");
        }
        for (String path : List.of(commentsPath(article), "/me/comments", "/admin/comments")) {
            for (String query : List.of("page=0", "page=100001", "pageSize=51", "pageSize=0", "page=x", "page=",
                    "page=1&page=2", "unknown=1", "authorId=1")) {
                code(call("GET", path + "?" + query, token, null), 400, "VALIDATION_ERROR");
            }
        }
        for (String query : List.of("parentId=01", "parentId=0")) {
            code(call("GET", commentsPath(article) + "?" + query, null, null), 400, "VALIDATION_ERROR");
        }
        for (String path : List.of("/me/comments?userId=1", "/me/comments?articleId=01", "/admin/comments?userId=0",
                "/admin/comments?status=4", "/me/comments?status=-1", likePath(article) + "?userId=1")) {
            code(call("GET", path, token, null), 400, "VALIDATION_ERROR");
        }
        String id = commentId(postComment(token, article, "body", null));
        for (String path : List.of("/me/comments/" + id, "/admin/comments/" + id, likePath(article))) {
            code(call("DELETE", path, token, "{\"userId\":\"1\"}"), 400, "VALIDATION_ERROR");
        }
        code(call("PUT", likePath(article), token, "{}"), 400, "VALIDATION_ERROR");
        code(call("GET", likePath(article), token, "{}"), 400, "VALIDATION_ERROR");
        MvcResult empty = call("GET", "/me/comments?page=100000&pageSize=50", token, null);
        code(empty, 200, "OK");
        assertThat(data(empty).get("page").asInt()).isEqualTo(100000);
        assertThat(data(empty).get("records").isEmpty()).isTrue();
    }

    @Test
    void interactionAuthenticationAndBothBackendPermissionsAreRequired() throws Exception {
        String admin = actor("interaction_guard_admin", 1);
        String member = actor("interaction_guard_member", 3);
        String author = actor("interaction_guard_author", 2);
        String article = published(admin, "Guards");
        String id = commentId(postComment(member, article, "body", null));
        for (String path : List.of(likePath(article), "/me/comments", "/admin/comments")) {
            code(call("GET", path, null, null), 401, "UNAUTHENTICATED");
        }
        code(postComment(null, article, "匿名", null), 401, "UNAUTHENTICATED");
        code(call("GET", "/admin/comments", member, null), 403, "FORBIDDEN");
        code(call("GET", "/admin/comments", author, null), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(3,40),(3,41),(3,42)");
        code(call("GET", "/admin/comments/" + id, member, null), 403, "FORBIDDEN");
        code(write("PUT", "/admin/comments/" + id + "/review", member, null, Map.of("status", 1)), 403, "FORBIDDEN");
        code(call("DELETE", "/admin/comments/" + id, member, null), 403, "FORBIDDEN");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=3 AND permission_id IN(40,41,42,43,44,45)");
        code(call("GET", likePath(article), member, null), 200, "OK");
        code(call("GET", "/me/comments", member, null), 200, "OK");
        code(postComment(member, article, "无权限", null), 403, "FORBIDDEN");
        code(call("DELETE", "/me/comments/" + id, member, null), 403, "FORBIDDEN");
        for (String method : List.of("PUT", "DELETE")) {
            code(call(method, likePath(article), member, null), 403, "FORBIDDEN");
        }
        jdbc.update("UPDATE sys_user SET status=0 WHERE username='interaction_guard_member'");
        code(call("GET", likePath(article), member, null), 403, "ACCOUNT_DISABLED");
        jdbc.update("UPDATE sys_user SET status=1 WHERE username='interaction_guard_member'");
        sessions.revokeAll(Long.parseLong(userId("interaction_guard_member")));
        code(call("GET", "/me/comments", member, null), 401, "UNAUTHENTICATED");
    }

    @Test
    void backendCommentScopesUseArticleAuthorAndAreIndependentPerOperation() throws Exception {
        String admin = actor("comment_scope_admin", 1);
        String author = actor("comment_scope_author", 2);
        String writer = actor("comment_scope_writer", 3);
        String ownArticle = published(author, "Scope-own");
        String otherArticle = published(admin, "Scope-other");
        String own = commentId(postComment(writer, ownArticle, "别人在我的文章评论", null));
        String other = commentId(postComment(author, otherArticle, "我在别人的文章评论", null));
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(2,40),(2,41)");
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(900,'comment_all','管理删除ALL'," +
                 "'ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(900,42)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,900)", userId("comment_scope_author"));
        JsonNode list = data(call("GET", "/admin/comments", author, null));
        assertThat(list.get("total").asString()).isEqualTo("1");
        assertThat(list.get("records").get(0).get("commentId").asString()).isEqualTo(own);
        assertThat(
                data(call("GET", "/admin/comments?userId=" + userId("comment_scope_author"), author, null)).get("total")
                        .asString()).isEqualTo("0");
        code(call("GET", "/admin/comments/" + other, author, null), 404, "NOT_FOUND");
        code(write("PUT", "/admin/comments/" + other + "/review", author, null, Map.of("status", 1)), 404, "NOT_FOUND");
        code(review(author, own, 1), 200, "OK");
        code(call("DELETE", "/admin/comments/" + other, author, null), 200, "OK");
        // 反过来给 query/review ALL、moderate SELF，确认查询能力不会扩大删除范围。
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=900");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(900,40),(900,41),(2,42)");
        code(call("GET", "/admin/comments/" + other, author, null), 200, "OK");
        code(call("DELETE", "/admin/comments/" + other, author, null), 404, "NOT_FOUND");
        code(call("DELETE", "/admin/comments/" + own, author, null), 200, "OK");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=2 AND permission_id IN(40,41,42)");
    }

    @Test
    void ownCommentFilterAndDeleteNeverExpandWithAllScope() throws Exception {
        String admin = actor("own_comment_admin", 1);
        String member = actor("own_comment_member", 3);
        String article = published(admin, "Own-comments");
        String theirs = commentId(postComment(member, article, "别人的原文", null));
        String own = commentId(postComment(admin, article, "我的原文", null));
        code(call("DELETE", "/me/comments/" + theirs, admin, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/me/comments?articleId=" + article + "&status=0", admin, null)).get("total")
                .asString()).isEqualTo("1");
        for (int i = 0; i < 2; i++) {
            MvcResult deleted = call("DELETE", "/me/comments/" + own, admin, null);
            code(deleted, 200, "OK");
            assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        }
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class,
                own)).isEqualTo("该评论已删除");
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class,
                theirs)).isEqualTo("别人的原文");
        assertThat(data(call("GET", "/me/comments?status=3", admin, null)).get("total").asString()).isEqualTo("1");
    }

    @Test
    void commentListsPaginateDirectChildrenWithStableOrderAndNoPendingLeaks() throws Exception {
        String token = actor("comment_layers", 1);
        String article = published(token, "Comment-layers");
        long user = Long.parseLong(userId("comment_layers"));
        comment(501, article, user, null, 1);
        comment(502, article, user, null, 0);
        comment(503, article, user, null, 2);
        comment(504, article, user, null, 3);
        comment(505, article, user, 501L, 1);
        comment(506, article, user, 501L, 3);
        comment(507, article, user, 501L, 0);
        comment(508, article, user, 501L, 2);
        comment(509, article, user, 505L, 1);
        jdbc.update("UPDATE blog_comment SET create_time='2026-01-01 00:00:00.000'");
        MvcResult roots = call("GET", commentsPath(article) + "?pageSize=1", token, null);
        code(roots, 200, "OK");
        assertThat(data(roots).get("total").asString()).isEqualTo("2");
        assertThat(data(roots).get("totalPages").asString()).isEqualTo("2");
        JsonNode placeholder = data(roots).get("records").get(0);
        assertThat(placeholder.get("commentId").asString()).isEqualTo("504");
        assertThat(placeholder.get("content").asString()).isEqualTo("该评论已删除");
        assertThat(placeholder.get("author").isNull()).isTrue();
        JsonNode root = data(call("GET", commentsPath(article) + "?page=2&pageSize=1", null, null)).get("records")
                .get(0);
        assertThat(root.get("commentId").asString()).isEqualTo("501");
        assertThat(root.get("replyCount").asString()).isEqualTo("2");
        JsonNode replies = data(call("GET", commentsPath(article) + "?parentId=501", null, null));
        assertThat(replies.get("total").asString()).isEqualTo("2");
        assertThat(replies.get("records").get(0).get("commentId").asString()).isEqualTo("506");
        assertThat(replies.get("records").get(1).get("parentId").asString()).isEqualTo("501");
        assertThat(replies.get("records").get(1).get("replyCount").asString()).isEqualTo("1");
        for (String id : List.of("502", "503", "999999")) {
            code(call("GET", commentsPath(article) + "?parentId=" + id, token, null), 404, "NOT_FOUND");
        }
        JsonNode empty = data(call("GET", commentsPath(article) + "?page=3&pageSize=1", null, null));
        assertThat(empty.get("page").asInt()).isEqualTo(3);
        assertThat(empty.get("records").isEmpty()).isTrue();
    }

    @Test
    void commentParentsMustBelongToArticleBeApprovedAndHaveVisibleAncestors() throws Exception {
        String token = actor("comment_parents", 1);
        String article = published(token, "Parents-one");
        String other = published(token, "Parents-two");
        long user = Long.parseLong(userId("comment_parents"));
        comment(601, other, user, null, 1);
        comment(602, article, user, null, 0);
        comment(603, article, user, null, 2);
        comment(604, article, user, null, 3);
        comment(605, article, user, 603L, 1);
        comment(606, article, user, 605L, 1);
        for (String parent : List.of("601", "999999")) {
            code(postComment(token, article, "跨文章或不存在", parent), 404, "NOT_FOUND");
            code(call("GET", commentsPath(article) + "?parentId=" + parent, null, null), 404, "NOT_FOUND");
        }
        for (String parent : List.of("602", "603", "604", "605", "606")) {
            code(postComment(token, article, "不可回复", parent), 409, "STATE_CONFLICT");
        }
        code(call("GET", commentsPath(article) + "?parentId=604", null, null), 200, "OK");
        code(review(token, "603", 1), 200, "OK");
        MvcResult deep = postComment(token, article, "保留真实深层父节点", "606");
        code(deep, 201, "OK");
        assertThat(data(deep).get("parentId").asString()).isEqualTo("606");
    }

    @Test
    void deepCommentChainsBeyondSqlRecursionLimitStayConsistentWithArticleCounts() throws Exception {
        String token = actor("comment_deep", 1);
        String article = published(token, "Deep-chain");
        long user = Long.parseLong(userId("comment_deep"));
        comment(1000, article, user, null, 0);
        for (long id = 1001; id <= 2005; id++) {
            comment(id, article, user, id - 1, 1);
        }
        code(call("GET", commentsPath(article) + "?parentId=2004", null, null), 404, "NOT_FOUND");
        code(postComment(token, article, "祖先待审", "2004"), 409, "STATE_CONFLICT");
        assertThat(articleComments(token, article)).isEqualTo("0");
        code(review(token, "1000", 1), 200, "OK");
        assertThat(articleComments(token, article)).isEqualTo("1006");
        JsonNode leaf = data(call("GET", commentsPath(article) + "?parentId=2004", null, null)).get("records").get(0);
        assertThat(leaf.get("commentId").asString()).isEqualTo("2005");
        code(call("DELETE", "/admin/comments/1000", token, null), 200, "OK");
        assertThat(articleComments(token, article)).isEqualTo("1005");
        code(call("GET", commentsPath(article) + "?parentId=2004", null, null), 200, "OK");
    }

    @Test
    void reviewAndPlaceholderChangesImmediatelyUpdatePublicChainAndArticleCounts() throws Exception {
        String token = actor("comment_counts", 1);
        String article = published(token, "Comment-counts");
        commentPolicy(true, false);
        String root = commentId(postComment(token, article, "root", null));
        String child = commentId(postComment(token, article, "child", root));
        String leaf = commentId(postComment(token, article, "leaf", child));
        String articleVersion = revision(token, article);
        assertThat(articleComments(token, article)).isEqualTo("3");
        code(review(token, root, 2), 200, "OK");
        assertThat(articleComments(token, article)).isEqualTo("0");
        code(call("GET", commentsPath(article) + "?parentId=" + child, token, null), 404, "NOT_FOUND");
        code(review(token, root, 1), 200, "OK");
        assertThat(articleComments(token, article)).isEqualTo("3");
        code(call("DELETE", "/me/comments/" + child, token, null), 200, "OK");
        assertThat(articleComments(token, article)).isEqualTo("2");
        JsonNode visible = data(call("GET", commentsPath(article) + "?parentId=" + child, null, null));
        assertThat(visible.get("records").get(0).get("commentId").asString()).isEqualTo(leaf);
        assertThat(data(call("GET", "/blog/articles/Comment-counts", null, null)).get("commentCount")
                .asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles", null, null)).get("records").get(0).get("commentCount")
                .asString()).isEqualTo("2");
        assertThat(revision(token, article)).isEqualTo(articleVersion);
    }

    @Test
    void reviewChecksRevisionBeforeIdempotenceAndDeletedTerminalState() throws Exception {
        String token = actor("comment_review", 1);
        String article = published(token, "Review");
        String id = commentId(postComment(token, article, "原文", null));
        String path = "/admin/comments/" + id + "/review";
        String original = commentRevision(token, id);
        assertThat(original).matches("\"[0-9a-f]{64}\"");
        code(write("PUT", path, token, null, Map.of("status", 1)), 428, "PRECONDITION_REQUIRED");
        for (String invalid : List.of("*", "abc", original + "," + original, "W/" + original)) {
            code(write("PUT", path, token, invalid, Map.of("status", 1)), 400, "VALIDATION_ERROR");
        }
        for (String body : List.of("{}", "{\"status\":null}", "{\"status\":0}", "{\"status\":3}",
                "{\"status\":\"1\"}", "{\"status\":1.2}", "{\"status\":true}", "{\"status\":1,\"remark\":\"x\"}",
                "{\"status\":1,\"status\":2}")) {
            code(call("PUT", path, token, body), 400, "VALIDATION_ERROR");
        }
        code(review(token, id, 1), 200, "OK");
        String approved = commentRevision(token, id);
        assertThat(approved).isNotEqualTo(original);
        code(write("PUT", path, token, original, Map.of("status", 1)), 412, "REVISION_CONFLICT");
        code(write("PUT", path, token, approved, Map.of("status", 1)), 200, "OK");
        assertThat(commentRevision(token, id)).isEqualTo(approved);
        code(review(token, id, 2), 200, "OK");
        code(review(token, id, 1), 200, "OK");
        code(call("DELETE", "/admin/comments/" + id, token, null), 200, "OK");
        code(call("DELETE", "/admin/comments/" + id, token, null), 200, "OK");
        String deleted = commentRevision(token, id);
        code(write("PUT", path, token, approved, Map.of("status", 1)), 412, "REVISION_CONFLICT");
        code(write("PUT", path, token, deleted, Map.of("status", 1)), 409, "STATE_CONFLICT");
        JsonNode detail = data(call("GET", "/admin/comments/" + id, token, null));
        assertThat(detail.get("content").asString()).isEqualTo("该评论已删除");
        assertThat(detail.get("author").isNull()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:comment' AND result=1",
                Integer.class)).isEqualTo(6);
    }

    @Test
    void commentRevisionIncludesBusinessFieldsButExcludesTimesAndDisplayInfo() throws Exception {
        String token = actor("comment_version", 1);
        String article = published(token, "Comment-version");
        String id = commentId(postComment(token, article, "原文", null));
        String version = commentRevision(token, id);
        jdbc.update("UPDATE blog_comment SET create_time='2020-01-01',update_time='2021-01-01' WHERE comment_id=?", id);
        jdbc.update("UPDATE sys_user SET nickname='新昵称' WHERE username='comment_version'");
        jdbc.update("UPDATE blog_article SET title='新标题' WHERE article_id=?", article);
        assertThat(commentRevision(token, id)).isEqualTo(version);
        jdbc.update("UPDATE blog_comment SET content='只有正文改变' WHERE comment_id=?", id);
        assertThat(commentRevision(token, id)).isNotEqualTo(version);
    }

    @Test
    void likesAreIdempotentPerCompositeKeyAndImmediatelyChangeArticleListsAndRankings() throws Exception {
        String admin = actor("like_admin", 1);
        String member = actor("like_member", 3);
        String first = published(admin, "Like-first");
        String second = published(admin, "Like-second");
        String version = revision(admin, first);
        for (int i = 0; i < 2; i++) {
            MvcResult liked = call("PUT", likePath(first), admin, null);
            code(liked, 200, "OK");
            assertThat(data(liked).get("liked").asBoolean()).isTrue();
            assertThat(data(liked).get("likeCount").asString()).isEqualTo("1");
        }
        code(call("PUT", likePath(first), member, null), 200, "OK");
        assertThat(data(call("GET", likePath(first), member, null)).get("likeCount").asString()).isEqualTo("2");
        assertThat(data(call("GET", likePath(second), member, null)).get("liked").asBoolean()).isFalse();
        assertThat(data(call("GET", "/blog/articles/Like-first", null, null)).get("likeCount").asString()).isEqualTo(
                "2");
        JsonNode rows = data(call("GET", "/blog/articles", null, null)).get("records");
        assertThat(rows.toString()).contains("\"likeCount\":\"2\"");
        for (String period : List.of("site", "month")) {
            JsonNode hot = data(call("GET", "/blog/article-rankings?metric=like&period=" + period, null, null)).get(0);
            assertThat(hot.get("articleId").asString()).isEqualTo(first);
            assertThat(hot.get("likeCount").asString()).isEqualTo("2");
        }
        for (int i = 0; i < 2; i++) {
            MvcResult removed = call("DELETE", likePath(first), admin, null);
            code(removed, 200, "OK");
            assertThat(data(removed).get("liked").asBoolean()).isFalse();
            assertThat(data(removed).get("likeCount").asString()).isEqualTo("1");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=? AND user_id=?",
                Integer.class, first, userId("like_member"))).isEqualTo(1);
        assertThat(revision(admin, first)).isEqualTo(version);
    }

    @Test
    void commentAndLikeRateLimitsUseUserAndIpAndPutDeleteShareOneWindow() throws Exception {
        String token = actor("interaction_rate", 1);
        String other = actor("interaction_rate_other", 3);
        String article = published(token, "Interaction-rate");
        for (int i = 0; i < 10; i++) {
            code(postComment(token, article, "评论" + i, null), 201, "OK");
        }
        for (String key : redis.keys("dl:article-test:rate:interface:comment:create:*")) {
            assertThat(redis.opsForValue().get(key)).isEqualTo("10");
        }
        MvcResult limited = postComment(token, article, "超限", null);
        code(limited, 429, "RATE_LIMITED");
        assertThat(Integer.parseInt(limited.getResponse().getHeader("Retry-After"))).isPositive();
        code(postComment(other, article, "同IP也超限", null), 429, "RATE_LIMITED");
        assertThat(redis.keys("dl:article-test:rate:interface:comment:create:*")).hasSize(3);
        MvcResult sameUser = mvc.perform(req("POST", commentsPath(article)).with(request -> {
                    request.setRemoteAddr("127.0.0.2");
                    return request;
                }).header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"content\":\"换IP仍超用户限制\"}")).andReturn();
        code(sameUser, 429, "RATE_LIMITED");
        for (int i = 0; i < 60; i++) {
            code(call(i % 2 == 0 ? "PUT" : "DELETE", likePath(article), token, null), 200, "OK");
        }
        code(call("DELETE", likePath(article), token, null), 429, "RATE_LIMITED");
        assertThat(redis.keys("dl:article-test:rate:interface:article:like:*")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like", Integer.class)).isZero();
    }

    @Test
    void interactionFixedPermissionsCannotBeDeletedAndDoNotExpandAdminGuard() throws Exception {
        String token = actor("interaction_fixed", 1);
        for (int id : List.of(40, 41, 42, 43, 44, 45)) {
            String path = "/admin/permissions/" + id;
            String version = call("GET", path, token, null).getResponse().getHeader("X-Revision");
            code(write("DELETE", path, token, version, null), 409, "STATE_CONFLICT");
        }
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).doesNotContainAnyElementsOf(
                AuthorizationProvider.INTERACTION_PERMISSIONS);
    }

    @Test
    void commentAuditFailureRollsBackReviewAndPlaceholderAndFailureIsRecordedAfterRollback() throws Exception {
        String token = actor("comment_audit", 1);
        String article = published(token, "Comment-audit");
        String id = commentId(postComment(token, article, "不能丢失的原文", null));
        String version = commentRevision(token, id);
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN" +
                 " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        code(review(token, id, 1), 503, "SERVICE_UNAVAILABLE");
        code(call("DELETE", "/admin/comments/" + id, token, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(commentRevision(token, id)).isEqualTo(version);
        assertThat(
                jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, id)).isEqualTo(
                "不能丢失的原文");
        finishAudits();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:comment' AND result=0",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:comment' AND result=1",
                Integer.class)).isZero();
    }

    @Test
    void failedCommentAndLikeInsertsLeaveNoPartialRowsOrCounts() throws Exception {
        String token = actor("interaction_insert_fail", 1);
        String article = published(token, "Insert-fail");
        for (String table : List.of("blog_comment", "blog_article_like")) {
            jdbc.execute(
                    "CREATE TRIGGER part7_fail BEFORE INSERT ON " + table + " FOR EACH ROW SIGNAL SQLSTATE '45000' " +
                     "SET MESSAGE_TEXT='isolated insert failure'");
            MvcResult result = table.equals("blog_comment") ? postComment(token, article, "失败原文", null)
                    : call("PUT", likePath(article), token, null);
            code(result, 503, "SERVICE_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
            jdbc.execute("DROP TRIGGER part7_fail");
        }
        assertThat(articleComments(token, article)).isEqualTo("0");
    }

    @Test
    void commentConfigLockDecidesLatestPolicyBeforeArticleLock() throws Exception {
        String token = actor("comment_config_wait", 1);
        String article = published(token, "Config-wait");
        MvcResult closed = waitingInteraction("blog_site_config",
                "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "comment",
                () -> postComment(token, article, "等待后关闭", null), () -> {
                    // 新增等配置时还没锁文章，配置修改不用倒过来等文章锁。
                    jdbc.queryForList("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE NOWAIT",
                            article);
                    commentPolicy(false, false);
                }, true);
        code(closed, 409, "STATE_CONFLICT");
        MvcResult approved = waitingInteraction("blog_site_config",
                "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "comment",
                () -> postComment(token, article, "等待后免审", null), () -> commentPolicy(true, false));
        code(approved, 201, "OK");
        assertThat(data(approved).get("status").asInt()).isEqualTo(1);
    }

    @Test
    void interactionWritesWaitingForArticleNeverWriteAfterItBecomesNonPublic() throws Exception {
        String author = actor("interaction_down_author", 1);
        String token = actor("interaction_down_wait", 3);
        for (String action : List.of("comment", "like", "unlike")) {
            String article = published(author, "Down-wait-" + action);
            if (action.equals("unlike")) {
                code(call("PUT", likePath(article), token, null), 200, "OK");
            }
            MvcResult result = waitingInteraction("blog_article",
                    "SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", article,
                    () -> action.equals("comment") ? postComment(token, article, "等待后写入", null)
                            : call(action.equals("like") ? "PUT" : "DELETE", likePath(article), token, null),
                    () -> jdbc.update("UPDATE blog_article SET status=2 WHERE article_id=?", article));
            code(result, 404, "NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE article_id=?", Integer.class,
                    article)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=?", Integer.class,
                    article))
                    .isEqualTo(action.equals("unlike") ? 1 : 0);
        }
    }

    @Test
    void interactionWritesRecheckTokenPermissionAndAccountAfterArticleLockWait() throws Exception {
        for (String action : List.of("comment", "like", "unlike", "own", "review", "moderate")) {
            for (String mode : List.of("token", "permission", "disabled")) {
                String username = "iw_" + action + "_" + mode;
                String token = actor(username, 1);
                String article = published(token, "IW-" + action + "-" + mode);
                String id = commentId(postComment(token, article, "保持原文", null));
                String version = commentRevision(token, id);
                if (action.equals("unlike")) {
                    code(call("PUT", likePath(article), token, null), 200, "OK");
                }
                String permission = switch (action) {
                    case "comment" -> "blog:comment:create";
                    case "like", "unlike" -> "blog:article:like";
                    case "own" -> "blog:comment:deleteOwn";
                    default -> "blog:comment:" + action;
                };
                Callable<MvcResult> attempt = () -> switch (action) {
                    case "comment" -> postComment(token, article, "禁止新增", null);
                    case "like", "unlike" ->
                            call(action.equals("like") ? "PUT" : "DELETE", likePath(article), token, null);
                    case "own" -> call("DELETE", "/me/comments/" + id, token, null);
                    case "review" ->
                            write("PUT", "/admin/comments/" + id + "/review", token, version, Map.of("status", 1));
                    default -> call("DELETE", "/admin/comments/" + id, token, null);
                };
                MvcResult result = waitingInteraction(mode.equals("disabled") ? "sys_user" : "blog_article",
                        mode.equals("disabled") ? "SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE"
                                : "SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE",
                        mode.equals("disabled") ? userId(username) : article, attempt, () -> {
                            // 直接改隔离数据才能在持有操作者锁时模拟授权变化，生产授权仍遵守用户锁。
                            if (mode.equals("token")) {
                                sessions.revokeAll(Long.parseLong(userId(username)));
                            } else if (mode.equals("permission")) {
                                jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code=?", permission);
                            } else {
                                jdbc.update("UPDATE sys_user SET status=0 WHERE username=?", username);
                            }
                        }, mode.equals("token"));
                code(result, mode.equals("token") ? 401 : 403,
                        mode.equals("token") ? "UNAUTHENTICATED" : mode.equals(
                                "disabled") ? "ACCOUNT_DISABLED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class,
                        id)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE article_id=?", Integer.class,
                        article)).isEqualTo(1);
                assertThat(
                        jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=?", Integer.class,
                                article))
                        .isEqualTo(action.equals("unlike") ? 1 : 0);
                jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_code=?", permission);
                // 同一 IP 的正常创建不因这组锁等待检查撞上限流窗口。
                redis.delete(redis.keys("dl:article-test:rate:interface:*"));
            }
        }
    }

    @Test
    void commentWritesRecheckIndependentSelfScopeAfterArticleLockWait() throws Exception {
        String admin = actor("comment_scope_wait_admin", 1);
        String author = actor("comment_scope_wait_author", 2);
        String article = published(author, "Scope-wait");
        String id = commentId(postComment(admin, article, "保留", null));
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(2,40),(2,41),(2,42)");
        for (String action : List.of("review", "moderate")) {
            jdbc.update("UPDATE blog_article SET author_id=? WHERE article_id=?", userId("comment_scope_wait_author"),
                    article);
            String version = commentRevision(author, id);
            MvcResult result = waitingInteraction("blog_article",
                    "SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", article,
                    () -> action.equals("review")
                            ? write("PUT", "/admin/comments/" + id + "/review", author, version, Map.of("status", 1))
                            : call("DELETE", "/admin/comments/" + id, author, null),
                    () -> jdbc.update("UPDATE blog_article SET author_id=? WHERE article_id=?",
                            userId("comment_scope_wait_admin"), article));
            code(result, 404, "NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class,
                    id)).isZero();
        }
    }

    @Test
    void replyingAfterArticleWaitRechecksFullParentChainAndArticleSwitch() throws Exception {
        String token = actor("comment_parent_wait", 1);
        String article = published(token, "Parent-wait");
        commentPolicy(true, false);
        String root = commentId(postComment(token, article, "root", null));
        String parent = commentId(postComment(token, article, "parent", root));
        for (String change : List.of("ancestor", "placeholder", "articleSwitch")) {
            jdbc.update("UPDATE blog_comment SET status=1 WHERE comment_id IN(?,?)", root, parent);
            jdbc.update("UPDATE blog_article SET allow_comment=1 WHERE article_id=?", article);
            MvcResult result = waitingInteraction("blog_article",
                    "SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", article,
                    () -> postComment(token, article, "不能依据等待前的状态回复", parent), () -> {
                        if (change.equals("articleSwitch")) {
                            jdbc.update("UPDATE blog_article SET allow_comment=0 WHERE article_id=?", article);
                        } else {
                            jdbc.update("UPDATE blog_comment SET status=? WHERE comment_id=?",
                                    change.equals("ancestor") ? 2 : 3, change.equals("ancestor") ? root : parent);
                        }
                    });
            code(result, 409, "STATE_CONFLICT");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(2);
    }

    @Test
    void commentReviewWaitingForCommentLockChecksLatestRevision() throws Exception {
        String token = actor("comment_revision_wait", 1);
        String writer = actor("comment_revision_writer", 3);
        String article = published(token, "Comment-revision-wait");
        String id = commentId(postComment(writer, article, "原文", null));
        String version = commentRevision(token, id);
        MvcResult result = waitingInteraction("blog_comment",
                "SELECT comment_id FROM blog_comment WHERE comment_id=? FOR UPDATE", id,
                () -> write("PUT", "/admin/comments/" + id + "/review", token, version, Map.of("status", 1)),
                () -> jdbc.update("UPDATE blog_comment SET content='持锁时替换的原文' WHERE comment_id=?", id), true);
        code(result, 412, "REVISION_CONFLICT");
        assertThat(
                jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, id)).isEqualTo(
                "持锁时替换的原文");
    }

    @Test
    void concurrentLikesCommentsReviewsAndDeletesKeepCompositeKeysChainsAndVersions() throws Exception {
        String admin = actor("interaction_parallel_admin", 1);
        String member = actor("interaction_parallel_member", 3);
        String article = published(admin, "Parallel-interaction");
        commentPolicy(true, false);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MvcResult>> work = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String actor = i % 2 == 0 ? admin : member;
                work.add(pool.submit(() -> call("PUT", likePath(article), actor, null)));
            }
            for (Future<MvcResult> result : work) {
                code(result.get(10, TimeUnit.SECONDS), 200, "OK");
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like", Integer.class)).isEqualTo(2);
            work.clear();
            for (int i = 0; i < 6; i++) {
                work.add(pool.submit(() -> postComment(member, article, "并发评论", null)));
            }
            for (Future<MvcResult> result : work) {
                code(result.get(10, TimeUnit.SECONDS), 201, "OK");
            }
            assertThat(articleComments(admin, article)).isEqualTo("6");
            String id = jdbc.queryForObject("SELECT MIN(comment_id) FROM blog_comment", String.class);
            String version = commentRevision(admin, id);
            work.clear();
            for (int status : List.of(2, 2)) {
                work.add(pool.submit(() -> write("PUT", "/admin/comments/" + id + "/review", admin, version,
                        Map.of("status", status))));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> result : work) {
                statuses.add(result.get(10, TimeUnit.SECONDS).getResponse().getStatus());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
            work.clear();
            work.add(pool.submit(() -> call("DELETE", "/admin/comments/" + id, admin, null)));
            work.add(pool.submit(() -> call("DELETE", "/me/comments/" + id, member, null)));
            for (Future<MvcResult> result : work) {
                code(result.get(10, TimeUnit.SECONDS), 200, "OK");
            }
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class,
                    id)).isEqualTo(3);
        }
    }

    @Test
    void ownAndAdminPlaceholderDeleteAllLiveStatesAndAdminCanModerateUnpublishedArticle() throws Exception {
        String token = actor("comment_delete_states", 1);
        String article = published(token, "Delete-states");
        long user = Long.parseLong(userId("comment_delete_states"));
        for (int status : List.of(0, 1, 2)) {
            long id = 3000 + status;
            comment(id, article, user, null, status);
            code(call("DELETE", "/me/comments/" + id, token, null), 200, "OK");
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class,
                    id)).isEqualTo(3);
        }
        String id = commentId(postComment(token, article, "待审直接驳回", null));
        code(review(token, id, 2), 200, "OK");
        code(status(token, article, 2), 200, "OK");
        code(call("DELETE", "/admin/comments/" + id, token, null), 200, "OK");
        assertThat(
                jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, id)).isEqualTo(
                "该评论已删除");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(4);
    }

    @Test
    void realInteractionLockTimeoutsReturn503AndPreserveRowsAndVersions() throws Exception {
        String token = actor("interaction_timeout", 1);
        String article = published(token, "Interaction-timeout");
        String id = commentId(postComment(token, article, "超时不能改原文", null));
        String version = commentRevision(token, id);
        for (String action : List.of("comment", "like", "review", "moderate")) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> holder = pool.submit(
                        () -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                            if (action.equals("like")) {
                                jdbc.queryForList("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE",
                                        article);
                            } else {
                                jdbc.queryForList(
                                        "SELECT config_key FROM blog_site_config WHERE config_key='comment' FOR " +
                                         "UPDATE");
                            }
                            locked.countDown();
                            await(release);
                        }));
                await(locked);
                Future<MvcResult> writer = pool.submit(() -> new TransactionTemplate(transactions).execute(state -> {
                    jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");
                    try {
                        return switch (action) {
                            case "comment" -> postComment(token, article, "超时新增", null);
                            case "like" -> call("PUT", likePath(article), token, null);
                            case "review" -> write("PUT", "/admin/comments/" + id + "/review", token, version,
                                    Map.of("status", 1));
                            default -> call("DELETE", "/admin/comments/" + id, token, null);
                        };
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    } finally {
                        state.setRollbackOnly();
                        jdbc.execute("SET SESSION innodb_lock_wait_timeout=50");
                    }
                }));
                try {
                    code(writer.get(8, TimeUnit.SECONDS), 503, "SERVICE_UNAVAILABLE");
                } finally {
                    release.countDown();
                }
                holder.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(commentRevision(token, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like", Integer.class)).isZero();
    }

    /** 用真实 InnoDB 等待点安排修改，调用方拿到结果时锁持有者已经提交。 */
    private MvcResult waitingInteraction(String table, String sql, Object id, Callable<MvcResult> attempt,
            Runnable change) throws Exception {
        return waitingInteraction(table, sql, id, attempt, change, false);
    }

    private MvcResult waitingInteraction(String table, String sql, Object id, Callable<MvcResult> attempt,
            Runnable change, boolean afterWait) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                jdbc.queryForList(sql, id);
                // 先改但不提交，请求仍读到旧值，等拿到锁后才应看到提交结果。
                if (!afterWait) {
                    change.run();
                }
                locked.countDown();
                await(release);
                if (afterWait) {
                    change.run();
                }
            }));
            await(locked);
            Future<MvcResult> writer = pool.submit(attempt);
            try {
                awaitLockWait(table);
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            return writer.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    @Test
    void friendLinkDefaultsDuplicatesAndAuditUseRealSavedId() throws Exception {
        String token = actor("link_defaults", 1);
        String first = newLink(token, Map.of("linkName", "  友站  ", "url", "https://example.test"));
        String second = newLink(token, Map.of("linkName", "友站", "url", "https://example.test"));
        JsonNode row = data(call("GET", "/admin/links/" + first, token, null));
        assertThat(row.get("linkName").asString()).isEqualTo("友站");
        assertThat(row.get("status").asInt()).isZero();
        assertThat(row.get("sortOrder").asInt()).isZero();
        assertThat(row.get("avatar").isNull()).isTrue();
        assertThat(row.get("description").isNull()).isTrue();
        assertThat(row.get("createTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(jdbc.queryForList(
                "SELECT target_id FROM sys_operation_log WHERE operation_target='blog:link' AND result=1",
                String.class))
                .containsExactlyInAnyOrder(first, second);
        assertThat(data(call("GET", "/blog/links", null, null)).get("total").asString()).isEqualTo("0");
    }

    @Test
    void friendLinkReviewSortAndDeleteImmediatelyChangePublicPages() throws Exception {
        String token = actor("link_public", 1);
        String first = newLink(token, linkBody("literal%_友站", 0, 1));
        newLink(token, linkBody("another", 0, 1));
        String hidden = newLink(token, linkBody("hidden", -1, 2));
        JsonNode page = data(call("GET", "/blog/links?pageSize=1", null, null));
        assertThat(page.get("total").asString()).isEqualTo("2");
        assertThat(page.get("totalPages").asString()).isEqualTo("2");
        assertThat(page.get("records").get(0).get("linkId").asString()).isEqualTo(first);
        assertThat(page.get("records").get(0).propertyNames()).containsExactlyInAnyOrder("linkId", "linkName", "url",
                "avatar", "description", "sortOrder");
        assertThat(data(mvc.perform(req("GET", "/admin/links").header("Authorization", "Bearer " + token)
                .param("keyword", "%_").param("status", "1")).andReturn()).get("total").asString()).isEqualTo("1");
        assertThat(data(call("GET", "/admin/links?status=2", token, null)).get("total").asString()).isEqualTo("1");
        String path = "/admin/links/" + hidden;
        code(write("PUT", path, token, dictionaryRevision(token, path), linkBody("now public", -1, 1)), 200, "OK");
        assertThat(
                data(call("GET", "/blog/links", null, null)).get("records").get(0).get("linkId").asString()).isEqualTo(
                hidden);
        path = "/admin/links/" + first;
        code(write("DELETE", path, token, dictionaryRevision(token, path), null), 200, "OK");
        assertThat(data(call("GET", "/blog/links", null, null)).get("total").asString()).isEqualTo("2");
        JsonNode empty = data(call("GET", "/blog/links?page=100000&pageSize=100", null, null));
        assertThat(empty.get("page").asInt()).isEqualTo(100000);
        assertThat(empty.get("records").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_friend_link WHERE link_id=?", Integer.class,
                first)).isZero();
        code(call("GET", "/admin/links/" + first, token, null), 404, "NOT_FOUND");
    }

    @Test
    void friendLinkPutRequiresAllFieldsAndClearsNullableValues() throws Exception {
        String token = actor("link_replace", 1);
        Map<String, Object> body = linkBody("友站", 5, 1);
        body.put("avatar", "https://example.test/image.png");
        body.put("description", "旧说明");
        String path = "/admin/links/" + newLink(token, body);
        String version = dictionaryRevision(token, path);
        for (String field : body.keySet()) {
            Map<String, Object> incomplete = new LinkedHashMap<>(body);
            incomplete.remove(field);
            code(write("PUT", path, token, version, incomplete), 400, "VALIDATION_ERROR");
        }
        body.put("avatar", "  ");
        body.put("description", null);
        code(write("PUT", path, token, version, body), 200, "OK");
        JsonNode row = data(call("GET", path, token, null));
        assertThat(row.get("avatar").isNull()).isTrue();
        assertThat(row.get("description").isNull()).isTrue();
    }

    @Test
    void friendLinkUrlsRejectDangerousAndInternalResourcesWithoutFetching() throws Exception {
        String token = actor("link_urls", 1);
        for (String field : List.of("url", "avatar")) {
            for (String url : List.of("javascript:bad()", "data:text/plain,bad", "file:///tmp/x", "//example.test/a",
                    "https://user:pass@example.test", "/api/v1/blog/articles/1/files/2/content",
                    "https://example.test/api/v1/blog/articles/1/files/2/content",
                    "https://example.test/api/v1/blog/articles/1/%66iles/2/content", "https://example.test/\nx")) {
                Map<String, Object> body = linkBody("友站", 0, 0);
                body.put(field, url);
                code(call("POST", "/admin/links", token, json.writeValueAsString(body)), 400, "VALIDATION_ERROR");
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_friend_link", Integer.class)).isZero();
        newLink(token, Map.of("linkName", "🌿".repeat(100), "url", "http://127.0.0.1:1/no-network"));
    }

    @Test
    void friendLinkJsonRejectsUnknownDuplicateTypesNullAndOverflow() throws Exception {
        String token = actor("link_strict", 1);
        for (String body : List.of("{}", "{\"linkName\":null,\"url\":\"https://e.test\"}",
                "{\"linkName\":1,\"url\":\"https://e.test\"}",
                "{\"linkName\":\"a\",\"linkName\":\"b\",\"url\":\"https://e.test\"}",
                "{\"linkName\":\"a\",\"url\":\"https://e.test\",\"status\":\"1\"}",
                "{\"linkName\":\"a\",\"url\":\"https://e.test\",\"sortOrder\":2147483648}",
                "{\"linkName\":\"a\",\"url\":\"https://e.test\",\"sortOrder\":1.2}")) {
            code(call("POST", "/admin/links", token, body), 400, "VALIDATION_ERROR");
        }
        for (String field : List.of("linkId", "authorId", "createTime", "present", "completeReplace")) {
            Map<String, Object> body = linkBody("a", 0, 0);
            body.put(field, "1");
            code(call("POST", "/admin/links", token, json.writeValueAsString(body)), 400, "VALIDATION_ERROR");
        }
        for (String field : List.of("linkName", "url", "sortOrder", "status")) {
            Map<String, Object> body = linkBody("a", 0, 0);
            body.put(field, null);
            code(call("POST", "/admin/links", token, json.writeValueAsString(body)), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/links", token, json.writeValueAsString(linkBody("🌿".repeat(101), 0, 0))), 400,
                "VALIDATION_ERROR");
    }

    @Test
    void friendLinkVersionsGuardUpdateAndDeleteAndIgnoreDatabaseTimes() throws Exception {
        String token = actor("link_versions", 1);
        String path = "/admin/links/" + newLink(token, linkBody("initial", 0, 0));
        String version = dictionaryRevision(token, path);
        for (String method : List.of("PUT", "DELETE")) {
            Object body = method.equals("PUT") ? linkBody("changed", 0, 1) : null;
            code(write(method, path, token, null, body), 428, "PRECONDITION_REQUIRED");
            code(write(method, path, token, "*", body), 400, "VALIDATION_ERROR");
            code(write(method, path, token, "\"" + "0".repeat(64) + "\"", body), 412, "REVISION_CONFLICT");
        }
        jdbc.update("UPDATE blog_friend_link SET create_time='2020-01-01',update_time='2021-01-01'");
        assertThat(dictionaryRevision(token, path)).isEqualTo(version);
        MvcResult updated = write("PUT", path, token, version, linkBody("changed", 0, 1));
        code(updated, 200, "OK");
        String next = updated.getResponse().getHeader("X-Revision");
        assertThat(next).isNotEqualTo(version);
        code(write("PUT", path, token, version, linkBody("changed", 0, 1)), 412, "REVISION_CONFLICT");
        code(write("PUT", path, token, next, linkBody("changed", 0, 1)), 200, "OK");
        assertThat(dictionaryRevision(token, path)).isEqualTo(next);
        code(write("DELETE", path, token, version, null), 412, "REVISION_CONFLICT");
        MvcResult deleted = write("DELETE", path, token, next, null);
        code(deleted, 200, "OK");
        assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        code(write("DELETE", path, token, next, null), 404, "NOT_FOUND");
    }

    @Test
    void publicConfigRequiresBothSiteWhitelistAndPublicFlag() throws Exception {
        String token = actor("config_public", 1);
        jdbc.update(
                "INSERT INTO blog_site_config(config_key,config_value,is_public) VALUES('secret',JSON_OBJECT" +
                 "('password','never-output'),1)");
        jdbc.update("UPDATE blog_site_config SET is_public=1 WHERE config_key IN('comment','message')");
        JsonNode visible = data(call("GET", "/blog/configs", null, null));
        assertThat(visible.size()).isEqualTo(1);
        assertThat(visible.get(0).get("configKey").asString()).isEqualTo("site");
        assertThat(visible.get(0).get("configValue").isObject()).isTrue();
        assertThat(visible.toString()).doesNotContain("reviewRequired", "never-output", "secret");
        jdbc.update("UPDATE blog_site_config SET is_public=0 WHERE config_key IN('comment','message')");
        JsonNode admin = data(call("GET", "/admin/configs", token, null));
        assertThat(admin.size()).isEqualTo(3);
        assertThat(admin.get(0).get("configKey").asString()).isEqualTo("comment");
        assertThat(admin.get(1).get("configKey").asString()).isEqualTo("message");
        assertThat(admin.get(2).get("configKey").asString()).isEqualTo("site");
        jdbc.update("UPDATE blog_site_config SET is_public=0 WHERE config_key='site'");
        publicCaches.getCache("configs").clear();
        assertThat(data(call("GET", "/blog/configs", null, null)).isEmpty()).isTrue();
        for (String key : List.of("secret", "Site", "unknown")) {
            code(call("GET", "/admin/configs/" + key, token, null), 404, "NOT_FOUND");
            code(write("PUT", "/admin/configs/" + key, token, null, configBody(siteValue(), 1)), 404, "NOT_FOUND");
        }
    }

    @Test
    void configReplacementNormalizesTextAndChangesPublicSiteImmediately() throws Exception {
        String token = actor("config_replace", 1);
        Map<String, Object> value = siteValue();
        value.put("title", " 🌿站点 ");
        value.put("startDate", "2024-02-29");
        Map<String, Object> body = configBody(value, 1);
        body.put("description", "  新说明  ");
        MvcResult saved = updateConfig(token, "site", body);
        code(saved, 200, "OK");
        JsonNode row = data(saved);
        assertThat(row.get("description").asString()).isEqualTo("新说明");
        assertThat(row.get("configValue").get("title").asString()).isEqualTo("🌿站点");
        assertThat(row.get("configValue").get("startDate").asString()).isEqualTo("2024-02-29");
        assertThat(row.get("updateTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(data(call("GET", "/blog/configs", null, null)).get(0).get("configValue").get("title")
                .asString()).isEqualTo("🌿站点");
        code(updateConfig(token, "site", configBody(value, 0)), 200, "OK");
        assertThat(data(call("GET", "/blog/configs", null, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/admin/configs/site", token, null)).get("description").isNull()).isTrue();
    }

    @Test
    void configInnerSchemaRejectsWrongFieldsTypesDatesAndVisibility() throws Exception {
        String token = actor("config_schema", 1);
        for (String key : List.of("comment", "message")) {
            for (Object value : List.of(Map.of(), Map.of("enabled", true), Map.of("enabled", 1, "reviewRequired", true),
                    Map.of("enabled", "true", "reviewRequired", false),
                    Map.of("enabled", true, "reviewRequired", false, "secret", "x"))) {
                code(updateConfig(token, key, configBody(value, 0)), 400, "VALIDATION_ERROR");
            }
            code(updateConfig(token, key, configBody(Map.of("enabled", true, "reviewRequired", true), 1)), 400,
                    "VALIDATION_ERROR");
        }
        for (String field : siteValue().keySet()) {
            Map<String, Object> value = siteValue();
            value.remove(field);
            code(updateConfig(token, "site", configBody(value, 1)), 400, "VALIDATION_ERROR");
        }
        for (String date : List.of("2023-02-29", "2024-13-01", "2024-2-01", "0000-01-01", "2024-01-01T00:00:00Z")) {
            Map<String, Object> value = siteValue();
            value.put("startDate", date);
            code(updateConfig(token, "site", configBody(value, 1)), 400, "VALIDATION_ERROR");
        }
        for (Object wrong : List.of(1, true, List.of("x"), Map.of("nested", "x"))) {
            Map<String, Object> value = siteValue();
            value.put("title", wrong);
            code(updateConfig(token, "site", configBody(value, 1)), 400, "VALIDATION_ERROR");
        }
        Map<String, Object> value = siteValue();
        value.put("smtpPassword", "never-store");
        code(updateConfig(token, "site", configBody(value, 1)), 400, "VALIDATION_ERROR");
    }

    @Test
    void configOuterJsonIsStrictAndObjectBytesAndUnicodeAreBounded() throws Exception {
        String token = actor("config_json", 1);
        String path = "/admin/configs/site";
        String version = dictionaryRevision(token, path);
        for (String body : List.of("{}", "{\"configValue\":null,\"description\":null,\"isPublic\":1}",
                "{\"configValue\":[],\"description\":null,\"isPublic\":1}",
                "{\"configValue\":{},\"description\":null,\"isPublic\":\"1\"}",
                "{\"configValue\":{\"enabled\":true,\"enabled\":false},\"description\":null,\"isPublic\":0}")) {
            code(mvc.perform(req("PUT", path).header("Authorization", "Bearer " + token)
                            .header("If-Revision", version).contentType("application/json").content(body)).andReturn(), 400,
                    "VALIDATION_ERROR");
        }
        for (String field : List.of("configValue", "description", "isPublic")) {
            Map<String, Object> body = configBody(siteValue(), 1);
            body.remove(field);
            code(write("PUT", path, token, version, body), 400, "VALIDATION_ERROR");
        }
        for (String field : List.of("configKey", "updateTime", "descriptionPresent", "completeReplace")) {
            Map<String, Object> body = configBody(siteValue(), 1);
            body.put(field, "x");
            code(write("PUT", path, token, version, body), 400, "VALIDATION_ERROR");
        }
        Map<String, Object> value = siteValue();
        value.put("title", "🌿".repeat(100));
        code(updateConfig(token, "site", configBody(value, 1)), 200, "OK");
        value.put("title", "🌿".repeat(101));
        code(updateConfig(token, "site", configBody(value, 1)), 400, "VALIDATION_ERROR");
        value.put("title", "🌿".repeat(5000));
        code(updateConfig(token, "site", configBody(value, 1)), 400, "VALIDATION_ERROR");
    }

    @Test
    void configRevisionIgnoresJsonPropertyOrderAndTimesButProtectsAllBusinessFields() throws Exception {
        String token = actor("config_version", 1);
        String path = "/admin/configs/site";
        String original = dictionaryRevision(token, path);
        jdbc.update(
                "UPDATE blog_site_config SET config_value=JSON_OBJECT('startDate',NULL,'publicSecurityRecord',''," +
                 "'icp','','description','记录与分享','title','DriftingLeaves'),update_time='2020-01-01' WHERE " +
                  "config_key='site'");
        assertThat(dictionaryRevision(token, path)).isEqualTo(original);
        Map<String, Object> body = configBody(siteValue(), 1);
        code(write("PUT", path, token, null, body), 428, "PRECONDITION_REQUIRED");
        code(write("PUT", path, token, "*", body), 400, "VALIDATION_ERROR");
        MvcResult updated = write("PUT", path, token, original, body);
        code(updated, 200, "OK");
        String next = updated.getResponse().getHeader("X-Revision");
        assertThat(next).isNotEqualTo(original);
        code(write("PUT", path, token, original, body), 412, "REVISION_CONFLICT");
        code(write("PUT", path, token, next, body), 200, "OK");
        assertThat(dictionaryRevision(token, path)).isEqualTo(next);
        body.put("description", "new description");
        code(write("PUT", path, token, next, body), 200, "OK");
        assertThat(dictionaryRevision(token, path)).isNotEqualTo(next);
    }

    @Test
    void configHttpChangesDirectlyControlCommentReviewSwitchesAndPreserveHistory() throws Exception {
        String token = actor("config_comment", 1);
        String article = published(token, "Config-Comment");
        code(updateConfig(token, "comment", configBody(Map.of("enabled", true, "reviewRequired", false), 0)), 200,
                "OK");
        String id = commentId(postComment(token, article, "免审", null));
        assertThat(
                jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, id)).isEqualTo(
                1);
        code(updateConfig(token, "comment", configBody(Map.of("enabled", false, "reviewRequired", true), 0)), 200,
                "OK");
        code(postComment(token, article, "关闭拒绝", null), 409, "STATE_CONFLICT");
        assertThat(data(call("GET", commentsPath(article), null, null)).get("total").asString()).isEqualTo("1");
        code(call("PUT", likePath(article), token, null), 200, "OK");
        code(call("DELETE", "/me/comments/" + id, token, null), 200, "OK");
        code(updateConfig(token, "comment", configBody(Map.of("enabled", true, "reviewRequired", true), 0)), 200, "OK");
        String pending = commentId(postComment(token, article, "管理员也待审", null));
        assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class,
                pending)).isZero();
        code(updateConfig(token, "message", configBody(Map.of("enabled", false, "reviewRequired", false), 0)), 200,
                "OK");
        assertThat(data(call("GET", "/admin/configs/message", token, null)).get("configValue").get("enabled")
                .asBoolean()).isFalse();
    }

    @Test
    void siteRoutesRequireLoginAdminAccessAndIndependentBusinessPermissions() throws Exception {
        String admin = actor("site_guard_admin", 1);
        String member = actor("site_guard_member", 3);
        String path = "/admin/links/" + newLink(admin, linkBody("guard", 0, 0));
        for (String route : List.of("/admin/links", path, "/admin/configs", "/admin/configs/site")) {
            code(call("GET", route, null, null), 401, "UNAUTHENTICATED");
            code(call("GET", route, member, null), 403, "FORBIDDEN");
        }
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 3,permission_id FROM sys_permission " +
                 "WHERE permission_id IN(50,51,52,53,70,71)");
        code(call("GET", "/admin/configs", member, null), 403, "FORBIDDEN");
        code(call("POST", "/admin/links", member, json.writeValueAsString(linkBody("no admin", 0, 0))), 403,
                "FORBIDDEN");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN(1,3) AND permission_id IN(51,52,53,71)");
        code(call("GET", path, admin, null), 200, "OK");
        code(call("GET", "/admin/configs/site", admin, null), 200, "OK");
        code(call("POST", "/admin/links", admin, json.writeValueAsString(linkBody("no create", 0, 0))), 403,
                "FORBIDDEN");
        code(write("PUT", path, admin, null, linkBody("no update", 0, 0)), 403, "FORBIDDEN");
        code(write("DELETE", path, admin, null, null), 403, "FORBIDDEN");
        code(write("PUT", "/admin/configs/site", admin, null, configBody(siteValue(), 1)), 403, "FORBIDDEN");
    }

    @Test
    void siteSelfPermissionsAreGlobalAndQueryIsNotRequiredForWrites() throws Exception {
        String admin = actor("site_self_admin", 1);
        String author = actor("site_self_author", 2);
        String path = "/admin/links/" + newLink(admin, linkBody("global", 0, 0));
        String linkVersion = dictionaryRevision(admin, path);
        String configVersion = dictionaryRevision(admin, "/admin/configs/site");
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id,permission_id) SELECT 2,permission_id FROM sys_permission " +
                 "WHERE permission_id IN(51,52,53,71)");
        code(call("GET", path, author, null), 403, "FORBIDDEN");
        code(write("PUT", path, author, linkVersion, linkBody("global changed", 0, 1)), 200, "OK");
        code(write("PUT", "/admin/configs/site", author, configVersion, configBody(siteValue(), 1)), 200, "OK");
        newLink(author, linkBody("author created", 0, 0));
        code(write("DELETE", path, author, dictionaryRevision(admin, path), null), 200, "OK");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(2,50),(2,70)");
        assertThat(data(call("GET", "/admin/links", author, null)).get("total").asString()).isEqualTo("1");
        code(call("GET", "/admin/configs", author, null), 200, "OK");
    }

    @Test
    void sitePathsQueriesAndNoBodyMethodsRejectUndeclaredInputs() throws Exception {
        String token = actor("site_parameters", 1);
        String path = "/admin/links/" + newLink(token, linkBody("guard", 0, 0));
        for (String id : List.of("0", "01", "-1", "1.2", "9223372036854775808")) {
            code(call("GET", "/admin/links/" + id, token, null), 400, "VALIDATION_ERROR");
        }
        for (String route : List.of("/blog/links", "/admin/links")) {
            for (String query : List.of("page=0", "page=100001", "pageSize=101", "pageSize=0", "page=x", "page=",
                    "page=1&page=2", "authorId=1")) {
                code(call("GET", route + "?" + query, token, null), 400, "VALIDATION_ERROR");
            }
        }
        for (String route : List.of("/blog/configs", "/admin/configs", "/admin/configs/site", path)) {
            code(call("GET", route + "?userId=1", token, null), 400, "VALIDATION_ERROR");
            code(call("GET", route, token, "{}"), 400, "VALIDATION_ERROR");
        }
        code(call("GET", "/blog/links?status=0", null, null), 400, "VALIDATION_ERROR");
        code(call("GET", "/admin/links?status=3", token, null), 400, "VALIDATION_ERROR");
        code(write("DELETE", path, token, dictionaryRevision(token, path), Map.of()), 400, "VALIDATION_ERROR");
    }

    @Test
    void siteFixedPermissionsCannotBeDeletedAndDoNotExpandLastAdminGuard() throws Exception {
        String token = actor("site_fixed", 1);
        for (int id : List.of(50, 51, 52, 53, 70, 71)) {
            String path = "/admin/permissions/" + id;
            code(write("DELETE", path, token, dictionaryRevision(token, path), null), 409, "STATE_CONFLICT");
        }
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).doesNotContain("blog:config:update", "blog:link:update");
    }

    @Test
    void siteAuditFailuresRollBackEveryWriteAndDoNotRecordConfigValues() throws Exception {
        String token = actor("site_audit", 1);
        String path = "/admin/links/" + newLink(token, linkBody("keep original", 0, 0));
        String version = dictionaryRevision(token, path);
        String configVersion = dictionaryRevision(token, "/admin/configs/site");
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN" +
                 " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        code(call("POST", "/admin/links", token, json.writeValueAsString(linkBody("cannot save", 0, 0))), 503,
                "SERVICE_UNAVAILABLE");
        code(write("PUT", path, token, version, linkBody("cannot replace", 0, 1)), 503, "SERVICE_UNAVAILABLE");
        code(write("DELETE", path, token, version, null), 503, "SERVICE_UNAVAILABLE");
        code(write("PUT", "/admin/configs/site", token, configVersion, configBody(siteValue(), 0)), 503,
                "SERVICE_UNAVAILABLE");
        assertThat(dictionaryRevision(token, path)).isEqualTo(version);
        assertThat(dictionaryRevision(token, "/admin/configs/site")).isEqualTo(configVersion);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_friend_link", Integer.class)).isEqualTo(1);
        finishAudits();
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE result=0", Integer.class)).isEqualTo(
                4);
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE result=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE error_message LIKE '%cannot%'",
                Integer.class)).isZero();
    }

    @Test
    void siteWritesWaitingForTheirRowsCheckNewlyCommittedVersions() throws Exception {
        String token = actor("site_version_wait", 1);
        String id = newLink(token, linkBody("before wait", 0, 0));
        String path = "/admin/links/" + id;
        for (String method : List.of("PUT", "DELETE")) {
            String version = dictionaryRevision(token, path);
            MvcResult result = waitingInteraction("blog_friend_link",
                    "SELECT link_id FROM blog_friend_link WHERE link_id=? FOR UPDATE", id,
                    () -> write(method, path, token, version,
                            method.equals("PUT") ? linkBody("do not save", 0, 0) : null),
                    () -> jdbc.update("UPDATE blog_friend_link SET sort_order=sort_order+1 WHERE link_id=?", id), true);
            code(result, 412, "REVISION_CONFLICT");
        }
        String configVersion = dictionaryRevision(token, "/admin/configs/site");
        MvcResult result = waitingInteraction("blog_site_config",
                "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "site",
                () -> write("PUT", "/admin/configs/site", token, configVersion, configBody(siteValue(), 1)),
                () -> jdbc.update(
                        "UPDATE blog_site_config SET description='committed while waiting' WHERE config_key='site'"),
                true);
        code(result, 412, "REVISION_CONFLICT");
    }

    @Test
    void siteWritesRecheckTokenPermissionAndAccountAfterRealLockWait() throws Exception {
        for (String kind : List.of("link", "config")) {
            for (String change : List.of("token", "permission", "account")) {
                String name = "site_wait_" + kind + "_" + change;
                String token = actor(name, 1);
                String id = kind.equals("link") ? newLink(token, linkBody("unchanged", 0, 0)) : "site";
                String path = kind.equals("link") ? "/admin/links/" + id : "/admin/configs/site";
                String version = dictionaryRevision(token, path);
                String permission = kind.equals("link") ? "blog:link:update" : "blog:config:update";
                // 账号变更也要先拿操作者锁，不能反向持有资源锁后再等同一个用户。
                String table = change.equals("account") ? "sys_user" : kind.equals(
                        "link") ? "blog_friend_link" : "blog_site_config";
                String sql = change.equals("account") ? "SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE"
                        : kind.equals("link") ? "SELECT link_id FROM blog_friend_link WHERE link_id=? FOR UPDATE"
                        : "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE";
                Object target = change.equals("account") ? userId(name) : id;
                MvcResult result = waitingInteraction(table, sql, target,
                        () -> write("PUT", path, token, version,
                                kind.equals("link") ? linkBody("forbidden", 0, 1) : configBody(siteValue(), 1)),
                        () -> {
                            switch (change) {
                                case "token" -> sessions.revokeAll(Long.parseLong(userId(name)));
                                case "permission" ->
                                        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code=?",
                                                permission);
                                default -> jdbc.update("UPDATE sys_user SET status=0 WHERE username=?", name);
                            }
                        }, !change.equals("account"));
                code(result, change.equals("token") ? 401 : 403,
                        change.equals("token") ? "UNAUTHENTICATED" : change.equals(
                                "permission") ? "FORBIDDEN" : "ACCOUNT_DISABLED");
                jdbc.update("UPDATE sys_permission SET status=1");
                jdbc.update("UPDATE sys_user SET status=1 WHERE username=?", name);
            }
        }
    }

    @Test
    void commentCreationWaitsForHttpConfigUpdateThenUsesCommittedPolicy() throws Exception {
        String admin = actor("config_wait_admin", 1);
        String member = actor("config_wait_member", 3);
        String article = published(admin, "Http-Config-Wait");
        for (boolean enabled : List.of(false, true)) {
            commentPolicy(true, false);
            Map<String, Object> body = configBody(Map.of("enabled", enabled, "reviewRequired", true), 0);
            // 配置 HTTP 更新加入隔离测试事务来延迟提交，实际观察另一用户的评论等同一行锁。
            MvcResult result = waitingInteraction("blog_site_config",
                    "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "comment",
                    () -> postComment(member, article, "等待配置提交", null), () -> {
                        try {
                            code(updateConfig(admin, "comment", body), 200, "OK");
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    });
            code(result, enabled ? 201 : 409, enabled ? "OK" : "STATE_CONFLICT");
            if (enabled) {
                assertThat(data(result).get("status").asInt()).isZero();
            }
        }
    }

    @Test
    void concurrentSiteUpdatesWithSameRevisionCommitOnlyOneResult() throws Exception {
        String first = actor("site_concurrent_first", 1);
        String second = actor("site_concurrent_second", 1);
        for (String kind : List.of("link", "config")) {
            String path = kind.equals("link") ? "/admin/links/" + newLink(first,
                    linkBody("initial", 0, 0)) : "/admin/configs/site";
            String version = dictionaryRevision(first, path);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<MvcResult> a = pool.submit(() -> write("PUT", path, first, version,
                        kind.equals("link") ? linkBody("first", 0, 1) : configBody(siteValue(), 0)));
                Future<MvcResult> b = pool.submit(() -> write("PUT", path, second, version,
                        kind.equals("link") ? linkBody("second", 0, 2) : configBody(siteValue(), 1)));
                assertThat(List.of(a.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                        b.get(10, TimeUnit.SECONDS).getResponse().getStatus()))
                        .containsExactlyInAnyOrder(200, 412);
            }
        }
    }

    /** 友链完整写模型，可空字段也保留，供 PUT 和缺失字段测试复用。 */
    private Map<String, Object> linkBody(String name, int sort, int status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("linkName", name);
        value.put("url", "https://example.test");
        value.put("avatar", null);
        value.put("description", null);
        value.put("sortOrder", sort);
        value.put("status", status);
        return value;
    }

    /** 经真实创建接口取得 ID，并确认详情 Location。 */
    private String newLink(String token, Map<String, Object> body) throws Exception {
        MvcResult result = call("POST", "/admin/links", token, json.writeValueAsString(body));
        code(result, 201, "OK");
        String id = data(result).get("linkId").asString();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/links/" + id);
        return id;
    }

    /** site 只包含五个固定字段，日期允许 null。 */
    private Map<String, Object> siteValue() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", "新站点");
        value.put("description", "新说明");
        value.put("icp", "");
        value.put("publicSecurityRecord", "");
        value.put("startDate", null);
        return value;
    }

    /** 完整替换配置，说明默认清空。 */
    private Map<String, Object> configBody(Object value, int isPublic) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configValue", value);
        body.put("description", null);
        body.put("isPublic", isPublic);
        return body;
    }

    /** 先走详情获取真实版本，再走配置更新 HTTP。 */
    private MvcResult updateConfig(String token, String key, Map<String, Object> body) throws Exception {
        String path = "/admin/configs/" + key;
        return write("PUT", path, token, dictionaryRevision(token, path), body);
    }

    private String published(String token, String slug) throws Exception {
        String id = create(token, slug, "文章正文");
        code(status(token, id, 1), 200, "OK");
        return id;
    }

    private String commentsPath(String article) {
        return "/blog/articles/" + article + "/comments";
    }

    private String likePath(String article) {
        return "/blog/articles/" + article + "/like";
    }

    private MvcResult postComment(String token, String article, String content, String parent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("parentId", parent);
        return call("POST", commentsPath(article), token, json.writeValueAsString(body));
    }

    private String commentId(MvcResult result) throws Exception {
        code(result, 201, "OK");
        return data(result).get("commentId").asString();
    }

    private String commentRevision(String token, String id) throws Exception {
        MvcResult result = call("GET", "/admin/comments/" + id, token, null);
        code(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    private MvcResult review(String token, String id, int status) throws Exception {
        return write("PUT", "/admin/comments/" + id + "/review", token, commentRevision(token, id),
                Map.of("status", status));
    }

    private String articleComments(String token, String article) throws Exception {
        MvcResult result = call("GET", "/admin/articles/" + article, token, null);
        code(result, 200, "OK");
        return data(result).get("commentCount").asString();
    }

    private void commentPolicy(boolean enabled, boolean review) {
        jdbc.update("UPDATE blog_site_config SET config_value=? WHERE config_key='comment'",
                json.writeValueAsString(Map.of("enabled", enabled, "reviewRequired", review)));
    }

    private Map<String, Object> categoryBody(String name, String slug, String description, int sort, int status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("categoryName", name);
        value.put("slug", slug);
        value.put("description", description);
        value.put("sortOrder", sort);
        value.put("status", status);
        return value;
    }

    private String dictionaryRevision(String token, String path) throws Exception {
        MvcResult result = call("GET", path, token, null);
        code(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    @Test
    void uploadPngAndJpegReturnsSafeMetadataRealBytesAndAudit() throws Exception {
        String token = actor("upload_images", 2);
        for (String extension : List.of("png", "jpg", "jpeg")) {
            byte[] image = image(extension);
            MvcResult result = upload(token, "../叶\\子." + extension, "text/html", image);
            code(result, 201, "OK");
            JsonNode value = data(result);
            String id = value.get("fileId").asString();
            assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/files/" + id);
            assertThat(result.getResponse().getHeader("X-Revision")).isNull();
            assertThat(value.get("uploaderId").asString()).isEqualTo(userId("upload_images"));
            assertThat(value.get("originalName").asString()).isEqualTo("..叶子." + extension);
            assertThat(value.get("sizeBytes").asString()).isEqualTo(Integer.toString(image.length));
            assertThat(value.get("storageType").asString()).isEqualTo("local");
            assertThat(value.has("objectKey")).isFalse();
            assertThat(value.has("bucket")).isFalse();
            MvcResult content = call("GET", "/admin/files/" + id + "/content", token, null);
            assertThat(content.getResponse().getContentAsByteArray()).containsExactly(image);
            assertThat(content.getResponse().getContentType()).isEqualTo(
                    extension.equals("png") ? "image/png" : "image/jpeg");
            assertThat(content.getResponse().getHeader("Content-Length")).isEqualTo(Integer.toString(image.length));
            assertThat(content.getResponse().getHeader("Content-Disposition")).contains("inline", "filename*=UTF-8''");
            assertThat(content.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(content.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(
                    jdbc.queryForObject("SELECT object_key FROM blog_file WHERE file_id=?", String.class, id)).matches(
                    "[A-Za-z0-9_-]+\\.(png|jpg|jpeg)");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:file' AND " +
                     "operation_type='upload' AND target_id=? AND result=1",
                    Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    void uploadRejectsSpoofedTypesTruncationAndUnsupportedImages() throws Exception {
        String token = actor("upload_types", 2);
        byte[] png = image("png");
        code(upload(token, "fake.jpg", "image/jpeg", png), 400, "VALIDATION_ERROR");
        code(upload(token, "fake.png", "image/png", image("jpg")), 400, "VALIDATION_ERROR");
        code(upload(token, "fake.png", "image/png", "<script>bad</script>".getBytes(StandardCharsets.UTF_8)), 400,
                "VALIDATION_ERROR");
        code(upload(token, "bad.png", "image/png", Arrays.copyOf(png, 25)), 400, "VALIDATION_ERROR");
        for (String name : List.of("leaf.svg", "leaf.webp", "leaf.pdf", "leaf", "/\\\r\n", "a".repeat(256) + ".png")) {
            code(upload(token, name, "image/png", png), 400, "VALIDATION_ERROR");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_file", Integer.class)).isZero();
        assertThat(uploadArtifacts()).isEmpty();
    }

    @Test
    void uploadChecksDimensionsBeforeDecodingLargeImage() throws Exception {
        String token = actor("upload_dimensions", 2);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10001, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        code(upload(token, "wide.png", "image/png", bytes.toByteArray()), 400, "VALIDATION_ERROR");
        bytes.reset();
        ImageIO.write(new BufferedImage(5001, 5000, BufferedImage.TYPE_BYTE_BINARY), "png", bytes);
        code(upload(token, "pixels.png", "image/png", bytes.toByteArray()), 400, "VALIDATION_ERROR");
        assertThat(uploadArtifacts()).isEmpty();
    }

    @Test
    void uploadRejectsEmptyOversizeAndTotalMultipartLimit() throws Exception {
        String token = actor("upload_limits", 2);
        code(upload(token, "empty.png", "image/png", new byte[0]), 400, "VALIDATION_ERROR");
        code(upload(token, "large.png", "image/png", new byte[10 * 1024 * 1024 + 1]), 413, "PAYLOAD_TOO_LARGE");
        var builder = multipart("/api/v1/admin/files").file(
                new MockMultipartFile("file", "leaf.png", "image/png", image("png")));
        builder.contextPath("/api/v1").servletPath("/admin/files").header("Authorization", "Bearer " + token);
        builder.with(request -> {
            request.setContent(new byte[11 * 1024 * 1024 + 1]);
            return request;
        });
        code(mvc.perform(builder).andReturn(), 413, "PAYLOAD_TOO_LARGE");
        assertThat(uploadArtifacts()).isEmpty();
    }

    @Test
    void uploadRejectsMissingDuplicateAndUnannouncedMultipartFields() throws Exception {
        String token = actor("upload_fields", 2);
        byte[] bytes = image("png");
        for (String variant : List.of("missing", "duplicate", "other", "uploader", "query")) {
            var builder = multipart("/api/v1/admin/files");
            if (!variant.equals("missing")) {
                builder.file(new MockMultipartFile("file", "leaf.png", "image/png", bytes));
            }
            if (variant.equals("duplicate")) {
                builder.file(new MockMultipartFile("file", "leaf2.png", "image/png", bytes));
            }
            if (variant.equals("other")) {
                builder.file(new MockMultipartFile("other", "leaf.png", "image/png", bytes));
            }
            if (variant.equals("uploader")) {
                builder.param("uploaderId", userId("upload_fields"));
            }
            if (variant.equals("query")) {
                builder.queryParam("uploaderId", userId("upload_fields"));
            }
            builder.contextPath("/api/v1").servletPath("/admin/files").header("Authorization", "Bearer " + token);
            code(mvc.perform(builder).andReturn(), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/files", token, "{}"), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void fileMetadataPagingFiltersAreLiteralAndRespectUploaderScope() throws Exception {
        String admin = actor("file_page_admin", 1);
        String author = actor("file_page_author", 2);
        String first = uploaded(author, "a%_.png");
        uploaded(author, "another.png");
        uploaded(admin, "a%_.png");
        JsonNode list = data(mvc.perform(req("GET", "/admin/files").param("keyword", "%_").param("pageSize", "1")
                .header("Authorization", "Bearer " + author)).andReturn());
        assertThat(list.get("total").asString()).isEqualTo("1");
        assertThat(list.get("records").get(0).get("fileId").asString()).isEqualTo(first);
        assertThat(data(call("GET", "/admin/files?uploaderId=" + userId("file_page_admin"), author, null)).get("total")
                .asString()).isEqualTo("0");
        assertThat(
                data(call("GET", "/admin/files?mimeType=image/jpeg", admin, null)).get("total").asString()).isEqualTo(
                "0");
        JsonNode empty = data(call("GET", "/admin/files?page=100&pageSize=1", admin, null));
        assertThat(empty.get("page").asInt()).isEqualTo(100);
        assertThat(empty.get("totalPages").asString()).isEqualTo("3");
        assertThat(empty.get("records").isEmpty()).isTrue();
        code(call("GET", "/admin/files/" + first, author, null), 200, "OK");
        for (String query : List.of("page=0", "pageSize=101", "page=1&page=2", "status=1", "uploaderId=01",
                "uploaderId=9223372036854775808", "keyword=", "mimeType=")) {
            code(call("GET", "/admin/files?" + query, admin, null), 400, "VALIDATION_ERROR");
        }
    }

    @Test
    void fileOperationsRequireOwnPermissionAndIndependentSelfScope() throws Exception {
        String admin = actor("file_guard_admin", 1);
        String author = actor("file_guard_author", 2);
        String member = actor("file_guard_member", 3);
        String id = uploaded(admin, "private.png");
        for (String path : List.of("/admin/files/" + id, "/admin/files/" + id + "/content")) {
            code(call("GET", path, author, null), 404, "NOT_FOUND");
            code(call("GET", path, member, null), 403, "FORBIDDEN");
            code(call("GET", path, null, null), 401, "UNAUTHENTICATED");
        }
        code(call("DELETE", "/admin/files/" + id, author, null), 404, "NOT_FOUND");
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(800,'file_query_all','查全部'," +
                 "'ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(800,60)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,800)", userId("file_guard_author"));
        code(call("GET", "/admin/files/" + id, author, null), 200, "OK");
        code(call("DELETE", "/admin/files/" + id, author, null), 404, "NOT_FOUND");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=61");
        code(upload(author, "a.png", "image/png", image("png")), 403, "FORBIDDEN");
    }

    @Test
    void originalPreviewDoesNotNeedPublicArticleAndMissingObjectsFail503() throws Exception {
        String token = actor("original_object", 2);
        String id = uploaded(token, "leaf.png");
        assertThat(call("GET", "/admin/files/" + id + "/content", token, null).getResponse().getStatus()).isEqualTo(
                200);
        Path path = objectPath(id);
        Files.delete(path);
        code(call("GET", "/admin/files/" + id + "/content", token, null), 503, "SERVICE_UNAVAILABLE");
        code(call("GET", "/admin/files/" + id, token, null), 200, "OK");
        code(call("DELETE", "/admin/files/" + id, token, null), 200, "OK");
    }

    @Test
    void publicAttachmentRequiresCurrentPublicStateAndRealReference() throws Exception {
        String token = actor("file_public", 1);
        String id = uploaded(token, "public.png");
        String article = create(token, "File-Public", "body");
        String path = context(article, id).substring("/api/v1".length());
        code(call("GET", path, token, null), 404, "NOT_FOUND");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article),
                body("File-Public", "![x](" + context(article, id) + ")")), 200, "OK");
        code(call("GET", path, token, null), 404, "NOT_FOUND");
        code(status(token, article, 1), 200, "OK");
        assertThat(call("GET", path, null, null).getResponse().getContentAsByteArray()).containsExactly(image("png"));
        for (String update : List.of("visibility=2", "status=2", "deleted_at=UTC_TIMESTAMP(3)",
                "publish_time=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY)", "status=0,publish_time=NULL")) {
            jdbc.update("UPDATE blog_article SET " + update + " WHERE article_id=?", article);
            code(call("GET", path, token, null), 404, "NOT_FOUND");
            jdbc.update(
                    "UPDATE blog_article SET visibility=1,status=1,deleted_at=NULL,publish_time=UTC_TIMESTAMP(3) " +
                     "WHERE article_id=?",
                    article);
        }
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article),
                body("File-Public", "no reference")), 200, "OK");
        code(call("GET", path, token, null), 404, "NOT_FOUND");
    }

    @Test
    void coverAndRawHtmlReferencesWorkWhileCodeAndOtherArticleDoNot() throws Exception {
        String token = actor("file_reference_read", 1);
        String id = uploaded(token, "image.png");
        String article = create(token, "File-Read-References", "body");
        Map<String, Object> body = body("File-Read-References", "`" + context(article, id) + "`");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), body), 200, "OK");
        String adminPath = "/admin/articles/" + article + "/files/" + id + "/content";
        code(call("GET", adminPath, token, null), 404, "NOT_FOUND");
        body.put("coverUrl", ORIGIN + context(article, id));
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), body), 200, "OK");
        assertThat(call("GET", adminPath, token, null).getResponse().getStatus()).isEqualTo(200);
        body.put("coverUrl", null);
        body.put("markdownContent", "<img src=\"" + context(article, id) + "\">");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), body), 200, "OK");
        assertThat(call("GET", adminPath, token, null).getResponse().getStatus()).isEqualTo(200);
        String other = create(token, "File-Other-Read", "body");
        code(call("GET", "/admin/articles/" + other + "/files/" + id + "/content", token, null), 404, "NOT_FOUND");
    }

    @Test
    void adminAttachmentChecksArticleAndFileScopesSeparately() throws Exception {
        String admin = actor("file_context_admin", 1);
        String author = actor("file_context_author", 2);
        String file = uploaded(admin, "admin.png");
        String article = create(author, "File-Independent", "body");
        code(write("PUT", "/admin/articles/" + article, admin, revision(admin, article),
                body("File-Independent", "![x](" + context(article, file) + ")")), 200, "OK");
        String path = "/admin/articles/" + article + "/files/" + file + "/content";
        code(call("GET", path, author, null), 404, "NOT_FOUND");
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(800,'context_file_all','查文件'," +
                 "'ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(800,60)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,800)", userId("file_context_author"));
        assertThat(call("GET", path, author, null).getResponse().getStatus()).isEqualTo(200);
        jdbc.update("UPDATE blog_article SET author_id=? WHERE article_id=?", userId("file_context_admin"), article);
        code(call("GET", path, author, null), 404, "NOT_FOUND");
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=10");
        code(call("GET", path, author, null), 403, "FORBIDDEN");
    }

    @Test
    void deletingFileScansDraftPrivateUnpublishedAndTrashReferences() throws Exception {
        String token = actor("file_delete_refs", 1);
        String file = uploaded(token, "protected.png");
        String article = create(token, "File-Delete-References", "body");
        Map<String, Object> value = body("File-Delete-References", "![x](" + context(article, file) + ")");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), value), 200, "OK");
        for (String state : List.of("status=0", "status=1,visibility=2,publish_time=UTC_TIMESTAMP(3)", "status=2",
                "deleted_at=UTC_TIMESTAMP(3)")) {
            jdbc.update("UPDATE blog_article SET " + state + " WHERE article_id=?", article);
            code(call("DELETE", "/admin/files/" + file, token, null), 409, "RESOURCE_IN_USE");
            assertThat(objectPath(file)).exists();
        }
        jdbc.update("UPDATE blog_article SET cover_url=?,deleted_at=NULL WHERE article_id=?", context(article, file),
                article);
        jdbc.update("UPDATE blog_article_content SET markdown_content='' WHERE article_id=?", article);
        code(call("DELETE", "/admin/files/" + file, token, null), 409, "RESOURCE_IN_USE");
        jdbc.update("UPDATE blog_article SET cover_url=NULL WHERE article_id=?", article);
        code(call("DELETE", "/admin/files/" + file, token, null), 200, "OK");
    }

    @Test
    void fileDeletionScansBeyondFirstBatchAndDoesNotTreatCodeAsReference() throws Exception {
        String token = actor("file_batch_refs", 1);
        String file = uploaded(token, "batch.png");
        for (int index = 0; index < 101; index++) {
            jdbc.update("INSERT INTO blog_article(article_id,author_id,title,slug) VALUES(?,?,?,?)", 80000 + index,
                    userId("file_batch_refs"), "batch", "batch-" + index);
            jdbc.update("INSERT INTO blog_article_content(article_id,markdown_content) VALUES(?,?)", 80000 + index,
                    index == 100 ? "![x](" + context("80100", file) + ")" : "body");
        }
        code(call("DELETE", "/admin/files/" + file, token, null), 409, "RESOURCE_IN_USE");
        jdbc.update("UPDATE blog_article_content SET markdown_content=? WHERE article_id=80100",
                "`" + context("80100", file) + "`");
        code(call("DELETE", "/admin/files/" + file, token, null), 200, "OK");
        code(call("DELETE", "/admin/files/" + file, token, null), 404, "NOT_FOUND");
    }

    @Test
    void uploadDatabaseAndAuditFailuresRemoveNewObjectsAndMetadata() throws Exception {
        String token = actor("file_upload_rollback", 1);
        List<Path> before = uploadArtifacts();
        for (String table : List.of("blog_file", "sys_operation_log")) {
            jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON " + table + " FOR EACH ROW "
                    + (table.equals(
                    "sys_operation_log") ? "BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET " +
                     "MESSAGE_TEXT='isolated file rollback'; END IF; END"
                    : "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated file rollback'"));
            code(upload(token, "rollback.png", "image/png", image("png")), 503, "SERVICE_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_file", Integer.class)).isZero();
            assertThat(uploadArtifacts()).containsExactlyElementsOf(before);
            jdbc.execute("DROP TRIGGER part7_fail");
        }
        uploaded(token, "success.png");
    }

    @Test
    void deletionStorageFailureKeepsMetadataAndCommitFailureAllowsRetry() throws Exception {
        String token = actor("file_delete_retry", 1);
        String id = uploaded(token, "retry.png");
        Path object = objectPath(id);
        byte[] bytes = Files.readAllBytes(object);
        Files.delete(object);
        Files.createDirectory(object);
        code(call("DELETE", "/admin/files/" + id, token, null), 503, "SERVICE_UNAVAILABLE");
        code(call("GET", "/admin/files/" + id, token, null), 200, "OK");
        Files.delete(object);
        Files.write(object, bytes);
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN" +
                 " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated delete rollback'; END IF; END");
        code(call("DELETE", "/admin/files/" + id, token, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(object).doesNotExist();
        code(call("GET", "/admin/files/" + id, token, null), 200, "OK");
        code(call("GET", "/admin/files/" + id + "/content", token, null), 503, "SERVICE_UNAVAILABLE");
        jdbc.execute("DROP TRIGGER part7_fail");
        code(call("DELETE", "/admin/files/" + id, token, null), 200, "OK");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:file' AND " +
                 "operation_type='delete' AND result=1 AND target_id=?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void fileDeleteWaitRechecksTokenPermissionAndUploaderScope() throws Exception {
        String admin = actor("file_wait_admin", 1);
        for (String change : List.of("token", "permission", "scope")) {
            String name = "file_wait_" + change;
            String token = actor(name, 2);
            String id = uploaded(token, "wait.png");
            MvcResult result = waitingInteraction("blog_file", "SELECT * FROM blog_file WHERE file_id=? FOR UPDATE", id,
                    () -> call("DELETE", "/admin/files/" + id, token, null), () -> {
                        switch (change) {
                            case "token" -> sessions.revokeAll(Long.parseLong(userId(name)));
                            case "permission" ->
                                    jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=62");
                            case "scope" -> jdbc.update("UPDATE blog_file SET uploader_id=? WHERE file_id=?",
                                    userId("file_wait_admin"), id);
                        }
                    }, true);
            code(result, change.equals("token") ? 401 : change.equals("permission") ? 403 : 404,
                    change.equals("token") ? "UNAUTHENTICATED" : change.equals(
                            "permission") ? "FORBIDDEN" : "NOT_FOUND");
            assertThat(objectPath(id)).exists();
            jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id=62");
        }
        assertThat(admin).isNotBlank();
    }

    @Test
    void waitingDeleteSeesReferenceCommittedWhileItWaitedForFileLock() throws Exception {
        String token = actor("file_wait_reference", 1);
        String id = uploaded(token, "wait-reference.png");
        String article = create(token, "File-Wait-Reference", "body");
        MvcResult result = waitingInteraction("blog_file", "SELECT * FROM blog_file WHERE file_id=? FOR UPDATE", id,
                () -> call("DELETE", "/admin/files/" + id, token, null),
                () -> jdbc.update("UPDATE blog_article_content SET markdown_content=? WHERE article_id=?",
                        "![x](" + context(article, id) + ")", article), true);
        code(result, 409, "RESOURCE_IN_USE");
        assertThat(objectPath(id)).exists();
    }

    @Test
    void waitingPublicAttachmentSeesUnpublishAndRemovedReference() throws Exception {
        String token = actor("file_wait_public", 1);
        String id = uploaded(token, "wait-public.png");
        String article = create(token, "File-Wait-Public", "body");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article),
                body("File-Wait-Public", "![x](" + context(article, id) + ")")), 200, "OK");
        code(status(token, article, 1), 200, "OK");
        String path = context(article, id).substring("/api/v1".length());
        for (String change : List.of("state", "reference")) {
            code(waitingInteraction("blog_article", "SELECT * FROM blog_article WHERE article_id=? FOR UPDATE", article,
                    () -> call("GET", path, null, null), () -> {
                        if (change.equals("state")) {
                            jdbc.update("UPDATE blog_article SET status=2 WHERE article_id=?", article);
                        } else {
                            jdbc.update("UPDATE blog_article_content SET markdown_content='' WHERE article_id=?",
                                    article);
                        }
                    }, true), 404, "NOT_FOUND");
            jdbc.update("UPDATE blog_article SET status=1 WHERE article_id=?", article);
        }
    }

    @Test
    void uploadsShareUserAndIpRateLimitAndFilePermissionsCannotBeDeleted() throws Exception {
        String token = actor("file_rate", 1);
        for (int index = 0; index < 10; index++) {
            code(upload(token, "empty.png", "image/png", new byte[0]), 400, "VALIDATION_ERROR");
        }
        MvcResult limited = upload(token, "leaf.png", "image/png", image("png"));
        code(limited, 429, "RATE_LIMITED");
        assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
        for (String permission : List.of("60", "61", "62")) {
            String path = "/admin/permissions/" + permission;
            code(write("DELETE", path, token, dictionaryRevision(token, path), null), 409, "STATE_CONFLICT");
        }
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).doesNotContain("blog:file:upload", "blog:file:delete");
    }

    @Test
    void fileReadAndDeleteRejectBodiesQueriesAndUnsafeStoredPaths() throws Exception {
        String token = actor("file_bad_paths", 1);
        String id = uploaded(token, "paths.png");
        for (String method : List.of("GET", "DELETE")) {
            code(call(method, "/admin/files/" + id, token, "{}"), 400, "VALIDATION_ERROR");
            code(call(method, "/admin/files/" + id + "?uploaderId=1", token, null), 400, "VALIDATION_ERROR");
        }
        for (String key : List.of("../outside.png", "/outside.png", "bad%2f.png")) {
            jdbc.update("UPDATE blog_file SET object_key=? WHERE file_id=?", key, id);
            code(call("GET", "/admin/files/" + id + "/content", token, null), 503, "SERVICE_UNAVAILABLE");
            code(call("DELETE", "/admin/files/" + id, token, null), 503, "SERVICE_UNAVAILABLE");
        }
        for (String invalid : List.of("01", "0", "-1", "9223372036854775808")) {
            code(call("GET", "/blog/articles/" + invalid + "/files/" + id + "/content", null, null), 400,
                    "VALIDATION_ERROR");
            code(call("GET", "/blog/articles/1/files/" + invalid + "/content", null, null), 400, "VALIDATION_ERROR");
        }
    }

    @Test
    void uploadWaitingForUserLockRechecksDisabledAccountAndToken() throws Exception {
        for (String change : List.of("account", "token")) {
            String name = "file_upload_wait_" + change;
            String token = actor(name, 2);
            MvcResult result = waitingInteraction("sys_user", "SELECT * FROM sys_user WHERE user_id=? FOR UPDATE",
                    userId(name),
                    () -> upload(token, "wait.png", "image/png", image("png")), () -> {
                        if (change.equals("account")) {
                            jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", userId(name));
                        } else {
                            sessions.revokeAll(Long.parseLong(userId(name)));
                        }
                    }, true);
            code(result, change.equals("account") ? 403 : 401,
                    change.equals("account") ? "ACCOUNT_DISABLED" : "UNAUTHENTICATED");
        }
        assertThat(uploadArtifacts()).isEmpty();
    }

    @Test
    void concurrentRealFileDeleteAndArticleReferenceCannotBothCommit() throws Exception {
        String uploader = actor("file_race_owner", 1);
        String editor = actor("file_race_editor", 1);
        String file = uploaded(uploader, "race.png");
        Path object = objectPath(file);
        String article = create(editor, "File-Real-Race", "body");
        String version = revision(editor, article);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var deleting = executor.submit(() -> {
                await(start);
                return call("DELETE", "/admin/files/" + file, uploader, null);
            });
            var referencing = executor.submit(() -> {
                await(start);
                return write("PUT", "/admin/articles/" + article, editor, version,
                        body("File-Real-Race", "![x](" + context(article, file) + ")"));
            });
            start.countDown();
            MvcResult removed = deleting.get(15, TimeUnit.SECONDS);
            MvcResult saved = referencing.get(15, TimeUnit.SECONDS);
            if (removed.getResponse().getStatus() == 200) {
                code(saved, 400, "VALIDATION_ERROR");
                assertThat(object).doesNotExist();
            } else {
                code(removed, 409, "RESOURCE_IN_USE");
                code(saved, 200, "OK");
                assertThat(object).exists();
            }
        }
    }

    /** 使用 JDK 自己生成真实小图片，不拿固定魔数冒充解码成功。 */
    private byte[] image(String extension) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), extension.equals("png") ? "png" : "jpeg",
                output);
        return output.toByteArray();
    }

    /** multipart 走真实 MVC 和业务，不 mock 文件存储或元数据。 */
    private MvcResult upload(String token, String name, String type, byte[] bytes) throws Exception {
        var builder = multipart("/api/v1/admin/files").file(new MockMultipartFile("file", name, type, bytes));
        builder.contextPath("/api/v1").servletPath("/admin/files");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder).andReturn();
    }

    /** 后续文章测试从上传响应取得 fileId，不自行伪造成功元数据。 */
    private String uploaded(String token, String name) throws Exception {
        MvcResult result = upload(token, name, "image/png", image("png"));
        code(result, 201, "OK");
        return data(result).get("fileId").asString();
    }

    /** 只在隔离测试里检查真实对象是否留下，HTTP 响应不能出现这条内部路径。 */
    private Path objectPath(String id) {
        return files.getLocalRoot()
                .resolve(jdbc.queryForObject("SELECT object_key FROM blog_file WHERE file_id=?", String.class, id));
    }

    /** 只比较本章随机对象和临时文件，保留较早测试的固定文件夹及历史产物。 */
    private List<Path> uploadArtifacts() throws Exception {
        try (var paths = Files.list(files.getLocalRoot())) {
            return paths.filter(path -> path.getFileName().toString().startsWith(".upload-")
                    || path.getFileName().toString().matches("[A-Za-z0-9_-]{40,}\\.(png|jpg|jpeg)")).sorted().toList();
        }
    }

    @Test
    void contentModelsRejectUnknownRepeatedAndEmptyQueryFields() throws Exception {
        String token = actor("model_content", 1);
        seedDictionary();
        String article = published(token, "Model-Content");
        for (String path : List.of("/blog/articles", "/blog/archives", "/blog/article-rankings", "/blog/categories",
                "/blog/tags",
                "/admin/articles", "/admin/categories", "/admin/tags")) {
            for (String query : List.of("uploaderId=1", "page=1&page=2", "page=")) {
                code(call("GET", path + "?" + query, token, null), 400, "VALIDATION_ERROR");
            }
        }
        for (String path : List.of("/blog/articles/Model-Content", "/admin/articles/" + article,
                "/admin/categories/700", "/admin/tags/701")) {
            code(call("GET", path + "?status=0", token, null), 400, "VALIDATION_ERROR");
            code(call("GET", path, token, "{}"), 400, "VALIDATION_ERROR");
        }
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                article)).isZero();
        code(call("GET", "/blog/articles?page=1&pageSize=5&keyword=Model", null, null), 200, "OK");
        code(call("GET", "/blog/article-rankings?period=site&metric=view&limit=5", null, null), 200, "OK");
    }

    @Test
    void rejectedContentWriteQueriesKeepTheArticleAndAuditUnchanged() throws Exception {
        String token = actor("model_content_write", 1);
        String article = create(token, "Model-Write", "原文");
        String version = revision(token, article);
        code(write("PUT", "/admin/articles/" + article + "?authorId=1", token, version,
                        body("Model-Write", "不能保存")),
                400, "VALIDATION_ERROR");
        code(call("POST", "/admin/categories?status=1", token,
                        "{\"categoryName\":\"不能创建\",\"slug\":\"No-Create\"}"),
                400, "VALIDATION_ERROR");
        assertThat(revision(token, article)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?",
                String.class, article)).isEqualTo("原文");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_category", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void articleCreationRechecksTokenAfterWaitingForItsCategory() throws Exception {
        String token = actor("model_create_lock", 1);
        seedDictionary();
        Map<String, Object> body = body("Model-Create-Lock", "不能保存");
        body.put("categoryId", "700");
        MvcResult result = waitingInteraction("blog_category",
                "SELECT category_id FROM blog_category WHERE category_id=? FOR UPDATE", 700,
                () -> call("POST", "/admin/articles", token, json.writeValueAsString(body)),
                () -> sessions.revokeAll(Long.parseLong(userId("model_create_lock"))), true);
        code(result, 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_content", Integer.class)).isZero();
        finishAudits();
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE result=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE result=0 AND error_message='UNAUTHENTICATED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void displayProfileSeparatesPublicDataAndRequiresCompleteReplacement() throws Exception {
        String token = actor("display_profile", 1);
        MvcResult empty = call("GET", "/admin/home/profile", token, null);
        code(empty, 200, "OK");
        assertThat(data(empty).isNull()).isTrue();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userId", userId("display_profile"));
        value.put("nickname", "公开主人");
        value.put("tagline", "");
        for (String field : List.of("description", "avatar", "website", "publicEmail", "githubUrl", "location")) {
            value.put(field, null);
        }
        code(write("PUT", "/admin/home/profile", token, empty.getResponse().getHeader("X-Revision"), value), 200, "OK");
        jdbc.update("UPDATE sys_user SET email='login-only@example.test' WHERE username='display_profile'");
        MvcResult publicResult = call("GET", "/home/profile", null, null);
        code(publicResult, 200, "OK");
        assertThat(data(publicResult).has("userId")).isFalse();
        assertThat(data(publicResult).get("publicEmail").isNull()).isTrue();
        String revision = call("GET", "/admin/home/profile", token, null).getResponse().getHeader("X-Revision");
        value.remove("location");
        code(write("PUT", "/admin/home/profile", token, revision, value), 400, "VALIDATION_ERROR");
        value.put("location", "上海");
        value.put("avatar", ORIGIN + "/api/v1/blog/articles/1/files/2/content");
        code(write("PUT", "/admin/home/profile", token, revision, value), 400, "VALIDATION_ERROR");
    }

    @Test
    void displaySocialFiltersHiddenAndChecksVersionsAndMailto() throws Exception {
        String token = actor("display_social", 1);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "联系");
        value.put("icon", "github");
        value.put("url", "mailto:contact@example.test");
        value.put("sortOrder", -1);
        value.put("status", 1);
        MvcResult created = call("POST", "/admin/home/social-links", token, json.writeValueAsString(value));
        code(created, 201, "OK");
        String id = data(created).get("socialId").asString();
        assertThat(created.getResponse().getHeader("X-Revision")).isNotNull();
        assertThat(data(call("GET", "/home/social-links", null, null)).size()).isEqualTo(1);
        value.put("url", "mailto:contact@example.test?subject=hello");
        code(write("PUT", "/admin/home/social-links/" + id, token, created.getResponse().getHeader("X-Revision"),
                value), 400, "VALIDATION_ERROR");
        value.put("url", "https://example.test/social");
        value.put("status", 0);
        code(write("PUT", "/admin/home/social-links/" + id, token, created.getResponse().getHeader("X-Revision"),
                value), 200, "OK");
        assertThat(data(call("GET", "/home/social-links", null, null)).size()).isZero();
        code(write("DELETE", "/admin/home/social-links/" + id, token, created.getResponse().getHeader("X-Revision"),
                null), 412, "REVISION_CONFLICT");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='home:social' AND target_id=? AND " +
                 "result=1",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    void experienceDatesAndMarkdownAreValidatedAndRenderedSafely() throws Exception {
        String token = actor("display_experience", 1);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("experienceType", 2);
        value.put("title", "项目");
        value.put("subtitle", null);
        value.put("logoUrl", null);
        value.put("content", "# 介绍\n<script>alert(1)</script>\n[安全](https://example.test)");
        value.put("startDate", "2020-02-29");
        value.put("endDate", null);
        value.put("sortOrder", 0);
        value.put("status", 1);
        code(call("POST", "/admin/cv/experiences", token, json.writeValueAsString(value)), 201, "OK");
        MvcResult result = call("GET", "/cv/experiences?experienceType=2", null, null);
        code(result, 200, "OK");
        JsonNode row = data(result).get("records").get(0);
        assertThat(row.has("content")).isFalse();
        assertThat(row.get("htmlContent").asString()).contains("<h1").doesNotContain("<script");
        value.put("endDate", "2019-01-01");
        code(call("POST", "/admin/cv/experiences", token, json.writeValueAsString(value)), 400, "VALIDATION_ERROR");
        value.put("endDate", null);
        value.put("content", "中".repeat(22000));
        code(call("POST", "/admin/cv/experiences", token, json.writeValueAsString(value)), 400, "VALIDATION_ERROR");
    }

    @Test
    void musicSupportsUnsignedDurationAndRequiresLyricPairWithoutFetchingUrls() throws Exception {
        String token = actor("display_music", 1);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", "曲目");
        value.put("artist", null);
        value.put("durationSeconds", 4294967295L);
        value.put("coverUrl", null);
        value.put("audioUrl", "https://media.example.test/audio.mp3");
        value.put("lyricUrl", null);
        value.put("lyricType", null);
        value.put("sortOrder", 0);
        value.put("status", 1);
        // Long在MVC输出用于ID，这里的请求用原始JSON数值表达时长。
        String body = json.writeValueAsString(value).replace("\"4294967295\"", "4294967295");
        code(call("POST", "/admin/music", token, body), 201, "OK");
        MvcResult result = call("GET", "/blog/music", null, null);
        code(result, 200, "OK");
        JsonNode row = data(result).get("records").get(0);
        assertThat(row.get("durationSeconds").isNumber()).isTrue();
        assertThat(row.get("durationSeconds").asLong()).isEqualTo(4294967295L);
        value.put("lyricType", "lrc");
        code(call("POST", "/admin/music", token, json.writeValueAsString(value)), 400, "VALIDATION_ERROR");
        value.put("durationSeconds", null);
        value.put("lyricUrl", "https://media.example.test/lyric.lrc");
        code(call("POST", "/admin/music", token, json.writeValueAsString(value)), 201, "OK");
    }

    @Test
    void newGlobalResourcesRejectSelfAndUnknownOrDuplicateQueries() throws Exception {
        String token = actor("display_self", 1);
        jdbc.update("UPDATE sys_role SET data_scope='SELF' WHERE role_id=1");
        for (String path : List.of("/admin/home/profile", "/admin/home/social-links", "/admin/cv/experiences",
                "/admin/cv/skills", "/admin/music", "/admin/operation-logs")) {
            code(call("GET", path, token, null), 403, "FORBIDDEN");
        }
        for (String path : List.of("/home/profile", "/home/social-links", "/cv/experiences", "/cv/skills",
                "/blog/music")) {
            code(call("GET", path + "?userId=1", null, null), 400, "VALIDATION_ERROR");
            code(call("GET", path, null, "{}"), 400, "VALIDATION_ERROR");
        }
        code(call("GET", "/cv/skills?page=1&page=2", null, null), 400, "VALIDATION_ERROR");
    }

    @Test
    void logQueryAndCleanupKeepTheirOwnAuditAndIndependentAllPermission() throws Exception {
        String token = actor("display_logs", 1);
        code(call("POST", "/admin/categories", token, "{\"categoryName\":\"审计来源\",\"slug\":\"log-source\"}"), 201,
                "OK");
        MvcResult listed = call("GET", "/admin/operation-logs?operationType=create&operationTarget=blog:category",
                token, null);
        code(listed, 200, "OK");
        JsonNode row = data(listed).get("records").get(0);
        assertThat(row.has("operationData")).isFalse();
        String id = row.get("logId").asString();
        MvcResult detail = call("GET", "/admin/operation-logs/" + id, token, null);
        code(detail, 200, "OK");
        assertThat(data(detail).get("operationData").isNull()).isTrue();
        code(call("DELETE", "/admin/operation-logs/" + id, token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE log_id=?", Integer.class,
                id)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='sys:log' AND target_id=? AND result=1",
                Integer.class, id)).isEqualTo(1);
        code(call("DELETE", "/admin/operation-logs/" + id, token, null), 404, "NOT_FOUND");
        code(call("GET", "/admin/operation-logs?begin=2026-10-30T01:00:00.000Z&end=2026-10-29T01:00:00.000Z", token,
                null), 400, "VALIDATION_ERROR");
    }

    @Test
    void subscriptionConfirmsOnlyCurrentRoundAndOwnerAndNeverExposesTickets() throws Exception {
        String token = actor("rss_owner", 3);
        String other = actor("rss_other", 3);
        String old = requestSubscription(token, "  Confirm@Example.TEST ");
        MvcResult own = call("GET", "/me/rss-subscription", token, null);
        code(own, 200, "OK");
        assertThat(data(own).get("email").asString()).isEqualTo("confirm@example.test");
        assertThat(data(own).get("status").asInt()).isZero();
        assertThat(data(own).has("ticket")).isFalse();
        assertThat(data(own).has("userId")).isFalse();
        String current = requestSubscription(token, "confirm@example.test");
        code(confirm(token, old), 400, "SUBSCRIPTION_TICKET_INVALID");
        code(confirm(other, current), 400, "SUBSCRIPTION_TICKET_INVALID");
        code(confirm(token, current), 200, "OK");
        code(confirm(token, current), 400, "SUBSCRIPTION_TICKET_INVALID");
        code(call("PUT", "/me/rss-subscription", token, "{\"email\":\"CONFIRM@example.test\"}"), 200, "OK");
        verify(mail, times(2)).send(eq("confirm@example.test"), eq("确认邮件订阅"), anyString());
        assertThat(jdbc.queryForObject(
                "SELECT verified_at IS NOT NULL AND subscribe_time IS NOT NULL AND unsubscribe_time IS NULL FROM " +
                 "blog_rss_subscription",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT email FROM sys_user WHERE username='rss_owner'", String.class)).isNull();
    }

    @Test
    void subscriptionUniqueEmailSmtpFailureAndPendingActivationHaveRealOutcomes() throws Exception {
        String admin = actor("rss_admin", 1);
        String member = actor("rss_member", 3);
        String other = actor("rss_second", 3);
        doThrow(new com.xuan.exception.BusinessException(503, "SERVICE_UNAVAILABLE", "邮件服务暂不可用")).when(mail)
                .send(anyString(), anyString(), anyString());
        code(call("PUT", "/me/rss-subscription", member, "{\"email\":\"pending@example.test\"}"), 503,
                "SERVICE_UNAVAILABLE");
        assertThat(data(call("GET", "/me/rss-subscription", member, null)).get("status").asInt()).isZero();
        code(call("PUT", "/me/rss-subscription", other, "{\"email\":\"PENDING@example.test\"}"), 409,
                "UNIQUE_CONFLICT");
        String id = jdbc.queryForObject("SELECT subscription_id FROM blog_rss_subscription", String.class);
        String rev = call("GET", "/admin/rss-subscriptions/" + id, admin, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/admin/rss-subscriptions/" + id + "/status", admin, rev, Map.of("status", 1)), 409,
                "STATE_CONFLICT");
        reset(mail);
        String ticket = requestSubscription(member, "pending@example.test");
        code(confirm(member, ticket), 200, "OK");
        code(call("DELETE", "/me/rss-subscription", member, null), 200, "OK");
        String first = jdbc.queryForObject("SELECT unsubscribe_time FROM blog_rss_subscription", String.class);
        code(call("DELETE", "/me/rss-subscription", member, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT unsubscribe_time FROM blog_rss_subscription", String.class)).isEqualTo(
                first);
    }

    @Test
    void subscriptionConsumedConfirmationIsNotRestoredAfterDatabaseRollback() throws Exception {
        String token = actor("rss_rollback", 3);
        String ticket = requestSubscription(token, "rollback@example.test");
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE UPDATE ON blog_rss_subscription FOR EACH ROW SIGNAL SQLSTATE " +
                 "'45000' SET MESSAGE_TEXT='isolated failure'");
        code(confirm(token, ticket), 503, "SERVICE_UNAVAILABLE");
        jdbc.execute("DROP TRIGGER part7_fail");
        assertThat(data(call("GET", "/me/rss-subscription", token, null)).get("status").asInt()).isZero();
        code(confirm(token, ticket), 400, "SUBSCRIPTION_TICKET_INVALID");
        code(confirm(token, requestSubscription(token, "rollback@example.test")), 200, "OK");
    }

    @Test
    void subscriptionNotificationRunsAfterFirstPublicCommitAndUnsubscribeIsScopedAndIdempotent() throws Exception {
        String admin = actor("rss_publish", 1);
        String token = actor("rss_recipient", 3);
        code(confirm(token, requestSubscription(token, "recipient@example.test")), 200, "OK");
        String article = published(admin, "rss-first-public");
        finishAudits();
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail, times(1)).send(eq("recipient@example.test"), eq("新文章发布"), text.capture());
        assertThat(text.getValue()).contains(ORIGIN + "/articles/rss-first-public");
        String ticket = fragmentTicket(text.getValue(), "unsubscribeTicket");
        code(status(admin, article, 2), 200, "OK");
        code(status(admin, article, 1), 200, "OK");
        finishAudits();
        verify(mail, times(1)).send(eq("recipient@example.test"), eq("新文章发布"), anyString());
        code(call("GET", "/blog/rss-subscriptions/unsubscribe?ticket=" + ticket, null, null), 405,
                "METHOD_NOT_ALLOWED");
        code(unsubscribe(ticket), 200, "OK");
        code(unsubscribe(ticket), 200, "OK");
        String newer = requestSubscription(token, "recipient@example.test");
        code(confirm(token, newer), 200, "OK");
        code(unsubscribe(ticket), 400, "SUBSCRIPTION_TICKET_INVALID");
        code(call("POST", "/blog/rss-subscriptions/unsubscribe", null, "{\"email\":\"recipient@example.test\"}"), 400,
                "VALIDATION_ERROR");
    }

    @Test
    void subscriptionPublicationRollbackAndPrivatePublicationNeverSendMail() throws Exception {
        String admin = actor("rss_no_leak", 1);
        String token = actor("rss_no_leak_member", 3);
        code(confirm(token, requestSubscription(token, "no-leak@example.test")), 200, "OK");
        reset(mail);
        String draft = create(admin, "rss-failed", "正文");
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN" +
                 " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated failure'; END IF; END");
        code(status(admin, draft, 1), 503, "SERVICE_UNAVAILABLE");
        jdbc.execute("DROP TRIGGER part7_fail");
        finishAudits();
        verifyNoInteractions(mail);
        assertThat(jdbc.queryForObject("SELECT status FROM blog_article WHERE article_id=?", Integer.class,
                draft)).isZero();
        Map<String, Object> privateBody = body("rss-private", "私密正文");
        privateBody.put("visibility", 2);
        MvcResult created = call("POST", "/admin/articles", admin, json.writeValueAsString(privateBody));
        code(created, 201, "OK");
        code(status(admin, data(created).get("articleId").asString(), 1), 200, "OK");
        finishAudits();
        verifyNoInteractions(mail);
    }

    @Test
    void feedAndSitemapContainOnlyPublicResourcesAndReturnXmlWithoutReadingCounts() throws Exception {
        String admin = actor("rss_xml", 1);
        seedDictionary();
        String visible = published(admin, "feed-visible");
        create(admin, "feed-hidden", "不能公开");
        jdbc.update(
                "UPDATE blog_article SET title='XML <title> & safe',summary='<summary>&摘要',category_id=700 WHERE " +
                 "article_id=?",
                visible);
        jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", visible);
        MvcResult feed = call("GET", "/blog/rss.xml", null, null);
        assertThat(feed.getResponse().getStatus()).isEqualTo(200);
        String xml = feed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(feed.getResponse().getContentType()).startsWith("application/rss+xml");
        assertThat(xml).contains("&lt;title&gt; &amp; safe", "&lt;summary&gt;&amp;摘要", "/articles/feed-visible")
                .doesNotContain("feed-hidden", "markdownContent");
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        MvcResult sitemap = call("GET", "/blog/sitemap.xml", null, null);
        assertThat(sitemap.getResponse().getStatus()).isEqualTo(200);
        String map = sitemap.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(map).contains("/articles/feed-visible", "/categories/category", "/tags/tag-one")
                .doesNotContain("feed-hidden");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                visible)).isZero();
        code(call("GET", "/blog/sitemap.xml?page=2", null, null), 404, "NOT_FOUND");
        code(status(admin, visible, 2), 200, "OK");
        assertThat(call("GET", "/blog/rss.xml", null, null).getResponse().getContentAsString()).doesNotContain(
                "feed-visible");
        assertThat(call("GET", "/blog/sitemap.xml", null, null).getResponse().getContentAsString()).doesNotContain(
                "tag-one", "category");
    }

    @Test
    void messagePrivateParentChainIsVisibleOnlyToItsInitiatorThroughPublicRoutes() throws Exception {
        String admin = actor("message_admin", 1);
        String owner = actor("message_owner", 3);
        String other = actor("message_other", 3);
        String root = createMessage(owner, null, "私密根原文", 1);
        reviewMessage(admin, root, 1);
        MvcResult reply = call("POST", "/admin/messages/" + root + "/replies", admin,
                json.writeValueAsString(messageEdit("后台私密回复")));
        code(reply, 201, "OK");
        assertThat(data(reply).get("author").get("userId").asString()).isEqualTo(userId("message_admin"));
        assertThat(data(reply).get("isSecret").asInt()).isEqualTo(1);
        for (String viewer : Arrays.asList(null, other, admin)) {
            assertThat(data(call("GET", "/blog/messages", viewer, null)).get("total").asString()).isEqualTo("0");
            code(call("GET", "/blog/messages?parentId=" + root, viewer, null), 404, "NOT_FOUND");
        }
        MvcResult ownThread = call("GET", "/blog/messages?parentId=" + root, owner, null);
        code(ownThread, 200, "OK");
        assertThat(data(ownThread).get("records").get(0).get("content").asString()).isEqualTo("后台私密回复");
        code(call("GET", "/me/messages/" + root, other, null), 404, "NOT_FOUND");
        code(call("POST", "/blog/messages", other, json.writeValueAsString(messageBody(root, "不能插入", 0))), 404,
                "NOT_FOUND");
    }

    @Test
    void messagePublicThreadSecretBranchAndDeepAncestorReviewNeverLeakHiddenContent() throws Exception {
        String admin = actor("message_branch_admin", 1);
        String owner = actor("message_branch_owner", 3);
        String other = actor("message_branch_other", 3);
        String root = createMessage(owner, null, "公开根", 0);
        reviewMessage(admin, root, 1);
        String secret = createMessage(other, root, "私密分支", 1);
        reviewMessage(admin, secret, 1);
        MvcResult reply = call("POST", "/admin/messages/" + secret + "/replies", admin,
                json.writeValueAsString(messageEdit("分支回复")));
        code(reply, 201, "OK");
        assertThat(data(call("GET", "/blog/messages?parentId=" + root, owner, null)).get("total").asString()).isEqualTo(
                "0");
        assertThat(
                data(call("GET", "/blog/messages?parentId=" + secret, other, null)).get("total").asString()).isEqualTo(
                "1");
        reviewMessage(admin, root, 2);
        code(call("GET", "/blog/messages?parentId=" + secret, other, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/me/messages", other, null)).get("records").get(0).get("content")
                .asString()).isEqualTo("私密分支");
    }

    @Test
    void messageEditsReenterReviewAndDeletionClearsOriginalWhileKeepingReplies() throws Exception {
        String admin = actor("message_edit_admin", 1);
        String owner = actor("message_edit_owner", 3);
        String root = createMessage(owner, null, "<script>纯文本</script>\n下一行", 0);
        reviewMessage(admin, root, 1);
        String child = createMessage(owner, root, "回复保留", 0);
        reviewMessage(admin, child, 1);
        MvcResult old = call("GET", "/me/messages/" + root, owner, null);
        String revision = old.getResponse().getHeader("X-Revision");
        assertThat(data(old).get("htmlContent").asString()).contains("&lt;script&gt;", "<br>")
                .doesNotContain("<script>");
        jdbc.update(
                "UPDATE blog_site_config SET config_value=JSON_OBJECT('enabled',TRUE,'reviewRequired',FALSE) WHERE " +
                 "config_key='message'");
        MvcResult edited = write("PUT", "/me/messages/" + root, owner, revision, messageEdit("修改后必须重审"));
        code(edited, 200, "OK");
        assertThat(data(edited).get("status").asInt()).isZero();
        assertThat(data(edited).get("editedAt").isNull()).isFalse();
        assertThat(data(call("GET", "/blog/messages", null, null)).get("total").asString()).isEqualTo("0");
        code(write("PUT", "/me/messages/" + root, owner, revision, messageEdit("旧版本")), 412, "REVISION_CONFLICT");
        reviewMessage(admin, root, 1);
        code(call("DELETE", "/me/messages/" + root, owner, null), 200, "OK");
        code(call("DELETE", "/me/messages/" + root, owner, null), 200, "OK");
        JsonNode placeholder = data(call("GET", "/blog/messages", null, null)).get("records").get(0);
        assertThat(placeholder.get("author").isNull()).isTrue();
        assertThat(placeholder.get("content").asString()).isEqualTo("该留言已删除");
        assertThat(data(call("GET", "/blog/messages?parentId=" + root, null, null)).get("total").asString()).isEqualTo(
                "1");
        code(write("PUT", "/me/messages/" + root, owner, edited.getResponse().getHeader("X-Revision"),
                messageEdit("复活")), 412, "REVISION_CONFLICT");
        String current = call("GET", "/admin/messages/" + root, admin, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/admin/messages/" + root + "/review", admin, current, Map.of("status", 1)), 409,
                "STATE_CONFLICT");
    }

    @Test
    void messageConfigurationLockDeterminesCreatePolicyAndRechecksRevokedToken() throws Exception {
        String token = actor("message_config_lock", 3);
        MvcResult closed = waitingInteraction("blog_site_config",
                "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "message",
                () -> call("POST", "/blog/messages", token, json.writeValueAsString(messageBody(null, "不能新增", 0))),
                () -> jdbc.update(
                        "UPDATE blog_site_config SET config_value=JSON_OBJECT('enabled',FALSE,'reviewRequired',TRUE) " +
                         "WHERE config_key='message'"));
        code(closed, 409, "STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message", Integer.class)).isZero();
        jdbc.update(
                "UPDATE blog_site_config SET config_value=JSON_OBJECT('enabled',TRUE,'reviewRequired',TRUE) WHERE " +
                 "config_key='message'");
        MvcResult revoked = waitingInteraction("blog_site_config",
                "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "message",
                () -> call("POST", "/blog/messages", token,
                        json.writeValueAsString(messageBody(null, "撤销后不能新增", 0))),
                () -> sessions.revokeAll(Long.parseLong(userId("message_config_lock"))), true);
        code(revoked, 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message", Integer.class)).isZero();
    }

    @Test
    void messageClosedConfigurationStillAllowsHistoryReviewAndDeletionButNotReplyOrEdit() throws Exception {
        String admin = actor("message_closed_admin", 1);
        String token = actor("message_closed", 3);
        String id = createMessage(token, null, "历史内容", 0);
        jdbc.update(
                "UPDATE blog_site_config SET config_value=JSON_OBJECT('enabled',FALSE,'reviewRequired',TRUE) WHERE " +
                 "config_key='message'");
        reviewMessage(admin, id, 1);
        String rev = call("GET", "/me/messages/" + id, token, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/me/messages/" + id, token, rev, messageEdit("不能编辑")), 409, "STATE_CONFLICT");
        code(call("POST", "/admin/messages/" + id + "/replies", admin,
                json.writeValueAsString(messageEdit("不能回复"))), 409, "STATE_CONFLICT");
        code(call("DELETE", "/me/messages/" + id, token, null), 200, "OK");
        code(call("GET", "/blog/messages?userId=1", token, null), 400, "VALIDATION_ERROR");
        code(call("GET", "/blog/messages", "invalid-token", null), 401, "UNAUTHENTICATED");
    }

    @Test
    void messageReplyNotificationUsesOnlyConfirmedSubscriptionAndContainsNoSecretText() throws Exception {
        String admin = actor("message_mail_admin", 1);
        String owner = actor("message_mail_owner", 3);
        jdbc.update("UPDATE sys_user SET email='unverified-login@example.test' WHERE username='message_mail_owner'");
        String root = createMessage(owner, null, "秘密根内容", 1);
        reviewMessage(admin, root, 1);
        code(call("POST", "/admin/messages/" + root + "/replies", admin,
                json.writeValueAsString(messageEdit("秘密回复一"))), 201, "OK");
        finishAudits();
        verifyNoInteractions(mail);
        code(confirm(owner, requestSubscription(owner, "confirmed-message@example.test")), 200, "OK");
        reset(mail);
        code(call("POST", "/admin/messages/" + root + "/replies", admin,
                json.writeValueAsString(messageEdit("秘密回复二"))), 201, "OK");
        finishAudits();
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail, times(1)).send(eq("confirmed-message@example.test"), eq("留言收到新回复"), text.capture());
        assertThat(text.getValue()).doesNotContain("秘密根内容", "秘密回复二", "unverified-login@example.test");
    }

    private Map<String, Object> messageBody(String parent, String content, int secret) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("parentId", parent);
        value.put("content", content);
        value.put("contentFormat", 0);
        value.put("isSecret", secret);
        value.put("notifyReply", 1);
        return value;
    }

    private Map<String, Object> messageEdit(String content) {
        return Map.of("content", content, "contentFormat", 0, "notifyReply", 1);
    }

    private String createMessage(String token, String parent, String content, int secret) throws Exception {
        MvcResult result = call("POST", "/blog/messages", token,
                json.writeValueAsString(messageBody(parent, content, secret)));
        code(result, 201, "OK");
        return data(result).get("messageId").asString();
    }

    private void reviewMessage(String token, String id, int status) throws Exception {
        String rev = call("GET", "/admin/messages/" + id, token, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/admin/messages/" + id + "/review", token, rev, Map.of("status", status)), 200, "OK");
    }

    private String requestSubscription(String token, String email) throws Exception {
        code(call("PUT", "/me/rss-subscription", token, json.writeValueAsString(Map.of("email", email))), 200, "OK");
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(anyString(), eq("确认邮件订阅"), text.capture());
        return fragmentTicket(text.getValue(), "confirmTicket");
    }

    private MvcResult confirm(String token, String ticket) throws Exception {
        return call("POST", "/me/rss-subscription/confirm", token, json.writeValueAsString(Map.of("ticket", ticket)));
    }

    private MvcResult unsubscribe(String ticket) throws Exception {
        return call("POST", "/blog/rss-subscriptions/unsubscribe", null,
                json.writeValueAsString(Map.of("ticket", ticket)));
    }

    private String fragmentTicket(String text, String name) {
        return text.split("#" + name + "=", 2)[1].split("\\s", 2)[0];
    }

    @Test
    void analyticsMasksIpSanitizesUrlsAndDoesNotDoubleCountReading() throws Exception {
        String admin = actor("analytics_article", 1);
        String article = published(admin, "analytics-visible");
        Map<String, Object> value = tracking("fingerprint-analytics-one",
                "/articles/analytics-visible?ticket=secret#fragment", article);
        value.put("referer", "https://search.example.test/path?accessToken=secret#ticket");
        MvcResult result = track(admin, ORIGIN, value);
        code(result, 200, "OK");
        assertThat(data(result).get("recorded").asBoolean()).isTrue();
        assertThat(data(result).size()).isEqualTo(1);
        Map<String, Object> saved = jdbc.queryForMap("SELECT * FROM blog_page_view");
        assertThat(saved.get("page_path")).isEqualTo("/articles/analytics-visible");
        assertThat(saved.get("referer")).isEqualTo("https://search.example.test/path");
        assertThat(saved.get("ip_address")).isEqualTo("203.0.113.0");
        assertThat(saved.get("user_id").toString()).isEqualTo(userId("analytics_article"));
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                article)).isZero();
        code(call("GET", "/blog/articles/analytics-visible", null, null), 200, "OK");
        code(track(null, ORIGIN, value), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                article)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_visitor", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isEqualTo(2);
    }

    @Test
    void analyticsRejectsInvalidOriginPathIdentityAndHiddenArticlesBeforeWriting() throws Exception {
        String admin = actor("analytics_reject", 1);
        String draft = create(admin, "analytics-hidden", "不能公开");
        Map<String, Object> value = tracking("fingerprint-analytics-reject", "/", null);
        code(track(null, null, value), 403, "FORBIDDEN");
        code(track(null, "https://evil.example.test", value), 403, "FORBIDDEN");
        value.put("siteCode", "home");
        code(track(null, ORIGIN, value), 403, "FORBIDDEN");
        value.put("siteCode", "blog");
        value.put("pagePath", "/safe/%2e%2e/admin");
        code(track(null, ORIGIN, value), 400, "VALIDATION_ERROR");
        value.put("pagePath", "/safe%3Fticket=secret");
        code(track(null, ORIGIN, value), 400, "VALIDATION_ERROR");
        value.put("pagePath", "/articles/analytics-hidden");
        value.put("articleId", draft);
        code(track(null, ORIGIN, value), 404, "NOT_FOUND");
        value.put("pagePath", "/");
        code(track(null, ORIGIN, value), 400, "VALIDATION_ERROR");
        value.put("articleId", null);
        value.put("userId", userId("analytics_reject"));
        code(track(null, ORIGIN, value), 400, "VALIDATION_ERROR");
        value.remove("userId");
        value.remove("referer");
        code(track(null, ORIGIN, value), 400, "VALIDATION_ERROR");
        value.put("referer", null);
        code(track("bad-token", ORIGIN, value), 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_visitor", Integer.class)).isZero();
    }

    @Test
    void analyticsRollsBackVisitorWhenPageViewInsertFails() throws Exception {
        jdbc.execute(
                "CREATE TRIGGER part7_fail BEFORE INSERT ON blog_page_view FOR EACH ROW SIGNAL SQLSTATE '45000' SET " +
                 "MESSAGE_TEXT='isolated failure'");
        code(track(null, ORIGIN, tracking("fingerprint-analytics-rollback", "/", null)), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_visitor", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isZero();
        jdbc.execute("DROP TRIGGER part7_fail");
        code(track(null, ORIGIN, tracking("fingerprint-analytics-rollback", "/", null)), 200, "OK");
    }

    @Test
    void analyticsRestrictionExpiresAndVisitorDeletionPreservesPv() throws Exception {
        String admin = actor("analytics_visitor_admin", 1);
        Map<String, Object> value = tracking("fingerprint-visitor-block", "/", null);
        code(track(null, ORIGIN, value), 200, "OK");
        String id = jdbc.queryForObject("SELECT visitor_id FROM blog_visitor", String.class);
        String revision = call("GET", "/admin/visitors/" + id, admin, null).getResponse().getHeader("X-Revision");
        code(track(null, ORIGIN, value), 200, "OK");
        assertThat(call("GET", "/admin/visitors/" + id, admin, null).getResponse().getHeader("X-Revision")).isEqualTo(
                revision);
        Map<String, Object> permanent = new LinkedHashMap<>();
        permanent.put("blockedUntil", null);
        code(write("PUT", "/admin/visitors/" + id + "/block", admin, revision, Map.of()), 400, "VALIDATION_ERROR");
        code(write("PUT", "/admin/visitors/" + id + "/block", admin, revision, permanent), 200, "OK");
        code(track(null, ORIGIN, value), 403, "VISITOR_BLOCKED");
        code(call("GET", "/blog/statistics", null, null), 200, "OK");
        jdbc.update("UPDATE blog_visitor SET blocked_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE visitor_id=?", id);
        code(track(null, ORIGIN, value), 200, "OK");
        revision = call("GET", "/admin/visitors/" + id, admin, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/admin/visitors/" + id + "/unblock", admin, revision, Map.of()), 400, "VALIDATION_ERROR");
        MvcResult unblocked = write("PUT", "/admin/visitors/" + id + "/unblock", admin, revision, null);
        code(unblocked, 200, "OK");
        code(write("DELETE", "/admin/visitors/" + id, admin, unblocked.getResponse().getHeader("X-Revision"), null),
                200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view WHERE visitor_id IS NULL",
                Integer.class)).isEqualTo(3);
        JsonNode publicStats = data(call("GET", "/blog/statistics", null, null));
        assertThat(publicStats.get("pageViewCount").asString()).isEqualTo("3");
        assertThat(publicStats.get("visitorCount").asString()).isEqualTo("0");
        String view = jdbc.queryForObject("SELECT MIN(view_id) FROM blog_page_view", String.class);
        code(call("DELETE", "/admin/page-views/" + view, admin, null), 200, "OK");
        code(call("DELETE", "/admin/page-views/" + view, admin, null), 404, "NOT_FOUND");
    }

    @Test
    void analyticsReportsUseUtcDistinctUvFirstVisitAndZeroFilledDays() throws Exception {
        String admin = actor("analytics_reports", 1);
        jdbc.update(
                "INSERT INTO blog_visitor(visitor_id,fingerprint,province,first_visit_time) VALUES(70001," +
                 "'fingerprint-report-one','江苏','2024-02-28 23:59:59.999'),(70002,'fingerprint-report-two',NULL," +
                  "'2024-02-29 00:00:00.000')");
        jdbc.update(
                "INSERT INTO blog_page_view(visitor_id,site_code,page_path,view_time) VALUES(70001,'blog','/'," +
                 "'2024-02-29 00:00:00.000'),(70001,'blog','/','2024-02-29 23:59:59.999'),(70002,'home','/'," +
                  "'2024-02-29 01:00:00.000')");
        String range = "?begin=2024-02-28&end=2024-03-01&siteCode=blog";
        MvcResult overview = call("GET", "/admin/statistics/overview" + range, admin, null);
        code(overview, 200, "OK");
        assertThat(data(overview).get("pageViewCount").asString()).isEqualTo("2");
        assertThat(data(overview).get("visitorCount").asString()).isEqualTo("1");
        assertThat(data(overview).get("newVisitorCount").asString()).isEqualTo("1");
        MvcResult trend = call("GET", "/admin/statistics/page-views" + range, admin, null);
        code(trend, 200, "OK");
        assertThat(data(trend).size()).isEqualTo(3);
        assertThat(data(trend).get(0).get("pageViewCount").asString()).isEqualTo("0");
        assertThat(data(trend).get(1).get("pageViewCount").asString()).isEqualTo("2");
        assertThat(data(trend).get(1).get("visitorCount").asString()).isEqualTo("1");
        MvcResult visitors = call("GET", "/admin/statistics/visitors" + range, admin, null);
        code(visitors, 200, "OK");
        assertThat(data(visitors).get(0).get("newVisitorCount").asString()).isEqualTo("1");
        assertThat(data(visitors).get(2).get("cumulativeNewVisitorCount").asString()).isEqualTo("1");
        MvcResult provinces = call("GET", "/admin/statistics/provinces" + range, admin, null);
        code(provinces, 200, "OK");
        assertThat(data(provinces).size()).isEqualTo(1);
        assertThat(data(provinces).get(0).get("visitorCount").asString()).isEqualTo("1");
        MvcResult views = call("GET",
                "/admin/page-views?begin=2024-02-29T00:00:00.000Z&end=2024-02-29T23:59:59.999Z&siteCode=blog", admin,
                null);
        code(views, 200, "OK");
        assertThat(data(views).get("total").asString()).isEqualTo("1");
    }

    @Test
    void analyticsArticleRankingsKeepHistoricalVisitsAndExcludeRecycledArticles() throws Exception {
        String admin = actor("analytics_rank", 1);
        String article = published(admin, "rank-history");
        String recycled = published(admin, "rank-recycled");
        jdbc.update(
                "INSERT INTO blog_page_view(article_id,site_code,page_path,view_time) VALUES(?,'blog'," +
                 "'/articles/rank-history','2024-02-29'),(?,'blog','/articles/rank-history','2024-02-29'),(?,'blog'," +
                  "'/articles/rank-recycled','2024-02-29')",
                article, article, recycled);
        code(status(admin, article, 2), 200, "OK");
        jdbc.update("UPDATE blog_article SET deleted_at=UTC_TIMESTAMP(3) WHERE article_id=?", recycled);
        MvcResult ranking = call("GET", "/admin/statistics/articles?begin=2024-02-29&end=2024-02-29&limit=1", admin,
                null);
        code(ranking, 200, "OK");
        assertThat(data(ranking).size()).isEqualTo(1);
        assertThat(data(ranking).get(0).get("articleId").asString()).isEqualTo(article);
        assertThat(data(ranking).get(0).get("pageViewCount").asString()).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class,
                article)).isZero();
    }

    @Test
    void analyticsStrictDateRangeAndAllScopeIncludeMaximumDatabaseDate() throws Exception {
        String admin = actor("analytics_dates", 1);
        for (String query : List.of("begin=2024-01-01", "begin=2024-02-30&end=2024-03-01",
                "begin=2024-03-01&end=2024-02-29", "begin=2023-01-01&end=2024-01-03", "begin=0999-01-01&end=0999-01-01",
                "siteCode=other")) {
            code(call("GET", "/admin/statistics/overview?" + query, admin, null), 400, "VALIDATION_ERROR");
        }
        MvcResult lastDay = call("GET", "/admin/statistics/page-views?begin=9999-12-31&end=9999-12-31", admin, null);
        code(lastDay, 200, "OK");
        assertThat(data(lastDay).size()).isEqualTo(1);
        code(call("GET", "/admin/statistics/articles?siteCode=blog", admin, null), 400, "VALIDATION_ERROR");
        jdbc.update("UPDATE sys_role SET data_scope='SELF' WHERE role_id=1");
        for (String path : List.of("/admin/visitors", "/admin/page-views", "/admin/statistics/overview",
                "/admin/messages", "/admin/rss-subscriptions")) {
            code(call("GET", path, admin, null), 403, "FORBIDDEN");
        }
    }

    @Test
    void analyticsRechecksTokenAfterVisitorWaitAndPublicArticleAfterArticleWait() throws Exception {
        String token = actor("analytics_wait", 1);
        String article = published(token, "analytics-wait");
        Map<String, Object> value = tracking("fingerprint-analytics-wait", "/articles/analytics-wait", article);
        code(track(null, ORIGIN, value), 200, "OK");
        String visitor = jdbc.queryForObject("SELECT visitor_id FROM blog_visitor", String.class);
        MvcResult revoked = waitingInteraction("blog_visitor",
                "SELECT visitor_id FROM blog_visitor WHERE visitor_id=? FOR UPDATE", visitor,
                () -> track(token, ORIGIN, value), () -> sessions.revokeAll(Long.parseLong(userId("analytics_wait"))),
                true);
        code(revoked, 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isEqualTo(1);
        MvcResult hidden = waitingInteraction("blog_article",
                "SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", article,
                () -> track(null, ORIGIN, value),
                () -> jdbc.update("UPDATE blog_article SET status=2 WHERE article_id=?", article));
        code(hidden, 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isEqualTo(1);
    }

    @Test
    void extendedWritesRecheckOriginalTokenAfterEightResourceLocks() throws Exception {
        for (String kind : List.of("social", "experience", "skill", "music", "visitor", "subscription", "log",
                "message")) {
            String name = "extended_lock_" + kind;
            String token = actor(name, 1);
            String user = userId(name);
            String table;
            String path;
            String id;
            switch (kind) {
                case "social" -> {
                    jdbc.update("INSERT INTO home_social_link(name,url) VALUES('锁','https://example.test')");
                    table = "home_social_link";
                    id = jdbc.queryForObject("SELECT MAX(social_id) FROM home_social_link", String.class);
                    path = "/admin/home/social-links/" + id;
                }
                case "experience" -> {
                    jdbc.update(
                            "INSERT INTO cv_experience(experience_type,title,content,start_date) VALUES(0,'锁','正文'," +
                             "'2020-01-01')");
                    table = "cv_experience";
                    id = jdbc.queryForObject("SELECT MAX(experience_id) FROM cv_experience", String.class);
                    path = "/admin/cv/experiences/" + id;
                }
                case "skill" -> {
                    jdbc.update("INSERT INTO cv_skill(name) VALUES('锁')");
                    table = "cv_skill";
                    id = jdbc.queryForObject("SELECT MAX(skill_id) FROM cv_skill", String.class);
                    path = "/admin/cv/skills/" + id;
                }
                case "music" -> {
                    jdbc.update(
                            "INSERT INTO blog_music(title,audio_url) VALUES('锁','https://example.test/audio.mp3')");
                    table = "blog_music";
                    id = jdbc.queryForObject("SELECT MAX(music_id) FROM blog_music", String.class);
                    path = "/admin/music/" + id;
                }
                case "visitor" -> {
                    jdbc.update("INSERT INTO blog_visitor(fingerprint) VALUES('fingerprint-lock-visitor')");
                    table = "blog_visitor";
                    id = jdbc.queryForObject("SELECT MAX(visitor_id) FROM blog_visitor", String.class);
                    path = "/admin/visitors/" + id;
                }
                case "subscription" -> {
                    jdbc.update("INSERT INTO blog_rss_subscription(user_id,email) VALUES(?,'lock@example.test')", user);
                    table = "blog_rss_subscription";
                    id = jdbc.queryForObject("SELECT MAX(subscription_id) FROM blog_rss_subscription", String.class);
                    path = "/admin/rss-subscriptions/" + id;
                }
                case "message" -> {
                    jdbc.update("INSERT INTO blog_message(user_id,content,status) VALUES(?,'锁',1)", user);
                    table = "blog_message";
                    id = jdbc.queryForObject("SELECT MAX(message_id) FROM blog_message", String.class);
                    path = "/admin/messages/" + id;
                }
                default -> {
                    jdbc.update(
                            "INSERT INTO sys_operation_log(user_id,operation_type,operation_target,result) VALUES(?," +
                             "'create','test:lock',1)",
                            user);
                    table = "sys_operation_log";
                    id = jdbc.queryForObject("SELECT MAX(log_id) FROM sys_operation_log", String.class);
                    path = "/admin/operation-logs/" + id;
                }
            }
            String primary = switch (kind) {
                case "subscription" -> "subscription_id";
                case "log" -> "log_id";
                default -> kind + "_id";
            };
            String revision = call("GET", path, token, null).getResponse().getHeader("X-Revision");
            MvcResult result = waitingInteraction(table,
                    "SELECT " + primary + " FROM " + table + " WHERE " + primary + "=? FOR UPDATE", id,
                    () -> write("DELETE", path, token, revision, null), () -> sessions.revokeAll(Long.parseLong(user)),
                    true);
            code(result, 401, "UNAUTHENTICATED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + primary + "=?", Integer.class,
                    id)).isEqualTo(1);
        }
        finishAudits();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE result=0 AND error_message='UNAUTHENTICATED'",
                Integer.class)).isEqualTo(8);
    }

    private Map<String, Object> tracking(String fingerprint, String path, String article) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fingerprint", fingerprint);
        value.put("siteCode", "blog");
        value.put("pagePath", path);
        value.put("pageTitle", null);
        value.put("referer", null);
        value.put("articleId", article);
        return value;
    }

    private MvcResult track(String token, String origin, Map<String, Object> value) throws Exception {
        var builder = req("POST", "/analytics/page-views").contentType("application/json")
                .content(json.writeValueAsString(value));
        if (origin != null) {
            builder.header("Origin", origin);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.header("User-Agent", "isolated-integration-test").header("X-Forwarded-For", "8.8.8.8");
        builder.with(request -> {
            request.setRemoteAddr("203.0.113.42");
            return request;
        });
        return mvc.perform(builder).andReturn();
    }

    @Test
    void sitemapPartitionsAllUrlsAtCountAndUtf8ByteLimits() throws Exception {
        String token = actor("sitemap_partition", 1);
        String digits = "(SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 " +
         "UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
        String sequence =
         "SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n AS n FROM " + digits + " a CROSS JOIN " + digits + " b CROSS " +
          "JOIN " + digits + " c CROSS JOIN " + digits + " d CROSS JOIN " + digits + " e WHERE e.n<5";
        jdbc.update(
                "INSERT INTO blog_article(author_id,title,slug,status,visibility,publish_time) SELECT ?,CONCAT('地图'," +
                 "n),CONCAT('map-',LPAD(n,5,'0')),1,1,UTC_TIMESTAMP(3) FROM (" + sequence + ") sequence_rows",
                userId("sitemap_partition"));
        String finalSlug = jdbc.queryForObject("SELECT slug FROM blog_article ORDER BY article_id DESC LIMIT 1",
                String.class);
        String index = call("GET", "/blog/sitemap.xml", null, null).getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(index).contains("<sitemapindex", ORIGIN + "/api/v1/blog/sitemap.xml?page=2");
        String first = call("GET", "/blog/sitemap.xml?page=1", null, null).getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String last = call("GET", "/blog/sitemap.xml?page=2", null, null).getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(first.split("<url>", -1).length - 1).isEqualTo(50000);
        assertThat(last.split("<url>", -1).length - 1).isEqualTo(3);
        assertThat(last).contains(finalSlug);
        String original = sites.getBlogBaseUrl();
        try {
            sites.setBlogBaseUrl(ORIGIN + "/" + "中".repeat(1000));
        publicCaches.getCache("sitemap").clear();
            MvcResult bounded = call("GET", "/blog/sitemap.xml?page=1", null, null);
            assertThat(bounded.getResponse().getStatus()).isEqualTo(200);
            byte[] bytes = bounded.getResponse().getContentAsByteArray();
            assertThat(bytes.length).isLessThanOrEqualTo(50 * 1024 * 1024);
            assertThat(new String(bytes, StandardCharsets.UTF_8).split("<url>", -1).length - 1).isLessThan(50000);
            String finalPage = call("GET", "/blog/sitemap.xml?page=3", null, null).getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(finalPage).contains(finalSlug);
        } finally {
            sites.setBlogBaseUrl(original);
        }
    }

    @Test
    void subscriptionConcurrentConfirmationAndExpiryRespectAtomicConsumption() throws Exception {
        String token = actor("rss_concurrent", 3);
        final String ticket = requestSubscription(token, "concurrent@example.test");
        String key = "dl:article-test:subscription:confirm:" + com.xuan.utils.TokenUtils.digest(ticket);
        assertThat(redis.getExpire(key)).isBetween(86390L, 86400L);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<MvcResult> left = executor.submit(() -> {
                await(start);
                return confirm(token, ticket);
            });
            Future<MvcResult> right = executor.submit(() -> {
                await(start);
                return confirm(token, ticket);
            });
            start.countDown();
            assertThat(List.of(left.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    right.get(10, TimeUnit.SECONDS).getResponse().getStatus())).containsExactlyInAnyOrder(200, 400);
        }
        code(call("DELETE", "/me/rss-subscription", token, null), 200, "OK");
        String expired = requestSubscription(token, "concurrent@example.test");
        redis.expire("dl:article-test:subscription:confirm:" + com.xuan.utils.TokenUtils.digest(expired),
                java.time.Duration.ZERO);
        code(confirm(token, expired), 400, "SUBSCRIPTION_TICKET_INVALID");
    }

    @Test
    void newFixedPermissionsCannotBeDeletedAndIndependentSelfScopeCannotWrite() throws Exception {
        String token = actor("extended_permissions", 1);
        for (int id : List.of(121, 131, 141, 151, 161, 171, 181, 191, 201, 221)) {
            MvcResult detail = call("GET", "/admin/permissions/" + id, token, null);
            code(detail, 200, "OK");
            code(write("DELETE", "/admin/permissions/" + id, token, detail.getResponse().getHeader("X-Revision"), null),
                    409, "STATE_CONFLICT");
        }
        jdbc.update(
                "INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(70000,'extended_self','SELF维护'," +
                 "'SELF')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,70000)", userId("extended_permissions"));
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=1 AND permission_id=131");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(70000,131)");
        code(call("GET", "/admin/home/social-links", token, null), 200, "OK");
        code(call("POST", "/admin/home/social-links", token,
                        "{\"name\":\"不能创建\",\"icon\":null,\"url\":\"https://example.test\",\"sortOrder\":0," +
                         "\"status\":1}"),
                403, "FORBIDDEN");
    }

    @Test
    void homeProfileRechecksTokenAfterGuardOwnerAndSingletonLocks() throws Exception {
        for (String stage : List.of("guard", "owner", "profile")) {
            String name = "home_lock_" + stage;
            String token = actor(name, 1);
            actor(name + "_owner", 3);
            String owner = userId(name + "_owner");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("userId", owner);
            value.put("nickname", "不能保存");
            value.put("tagline", "");
            for (String field : List.of("description", "avatar", "website", "publicEmail", "githubUrl", "location")) {
                value.put(field, null);
            }
            if (stage.equals("profile")) {
                jdbc.update("INSERT INTO home_profile(profile_id,user_id,nickname) VALUES(1,?,'原资料')", owner);
            }
            String revision = call("GET", "/admin/home/profile", token, null).getResponse().getHeader("X-Revision");
            String table = stage.equals("guard") ? "sys_role" : stage.equals("owner") ? "sys_user" : "home_profile";
            String primary = stage.equals("guard") ? "role_id" : stage.equals("owner") ? "user_id" : "profile_id";
            Object target = stage.equals("owner") ? owner : 1;
            MvcResult result = waitingInteraction(table,
                    "SELECT " + primary + " FROM " + table + " WHERE " + primary + "=? FOR UPDATE", target,
                    () -> write("PUT", "/admin/home/profile", token, revision, value),
                    () -> sessions.revokeAll(Long.parseLong(userId(name))), true);
            code(result, 401, "UNAUTHENTICATED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM home_profile WHERE nickname='不能保存'",
                    Integer.class)).isZero();
        }
        finishAudits();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='home:profile' AND result=0 AND " +
                 "error_message='UNAUTHENTICATED'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void discoveryAuthorFilterCombinesAllConditionsAndKeepsPublicIsolation() throws Exception {
        String owner = actor("discovery_owner", 1);
        String other = actor("discovery_other", 2);
        seedDictionary();
        Map<String, Object> value = body("Discovery-match", "literal needle%_");
        value.put("categoryId", "700");
        value.put("tagIds", List.of("701"));
        String id = data(call("POST", "/admin/articles", owner, json.writeValueAsString(value)))
                .get("articleId").asString();
        code(status(owner, id, 1), 200, "OK");
        String foreign = published(other, "Discovery-foreign");
        String privateId = published(owner, "Discovery-private");
        String future = published(owner, "Discovery-future");
        String recycled = published(owner, "Discovery-recycled");
        create(owner, "Discovery-draft", "literal needle%_");
        jdbc.update("UPDATE blog_article SET visibility=2 WHERE article_id=?", privateId);
        jdbc.update("UPDATE blog_article SET publish_time=UTC_TIMESTAMP(3)+INTERVAL 1 DAY WHERE article_id=?", future);
        code(write("DELETE", "/admin/articles/" + recycled, owner, revision(owner, recycled), null), 200, "OK");
        String prefix = "/blog/articles?authorId=" + userId("discovery_owner");
        String month = LocalDateTime.now(ZoneOffset.UTC).toString().substring(0, 7);
        MvcResult response = mvc.perform(req("GET", prefix + "&categoryId=700&tagId=701&month=" + month)
                .header("Authorization", "Bearer " + owner).param("keyword", "needle%_")).andReturn();
        code(response, 200, "OK");
        assertThat(data(response).get("total").asString()).isEqualTo("1");
        assertThat(data(response).get("records").get(0).get("articleId").asString()).isEqualTo(id);
        assertThat(data(call("GET", prefix, null, null)).get("total").asString()).isEqualTo("1");
        for (String suffix : List.of("&tagId=702", "&categoryId=999999", "&keyword=absent")) {
            assertThat(data(call("GET", prefix + suffix, null, null)).get("total").asString()).isEqualTo("0");
        }
        assertThat(data(call("GET", "/blog/articles?authorId=9223372036854775807", null, null)).get("total")
                .asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/articles?authorId=" + userId("discovery_other"), null, null))
                .get("records").get(0).get("articleId").asString()).isEqualTo(foreign);
        jdbc.update("UPDATE blog_category SET status=0 WHERE category_id=700");
        publicCaches.getCache("articleDetail").clear(); publicCaches.getCache("categories").clear();
        assertThat(data(call("GET", prefix + "&categoryId=700", owner, null)).get("total").asString()).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT SUM(view_count) FROM blog_article", Long.class)).isZero();
    }

    @Test
    void discoveryAuthorsUsePublicCountsStablePaginationAndSafeCurrentProfiles() throws Exception {
        String first = actor("discovery_author_first", 1);
        String second = actor("discovery_author_second", 2);
        String third = actor("discovery_author_third", 2);
        String empty = actor("discovery_author_empty", 3);
        String firstId = userId("discovery_author_first");
        String one = published(first, "Author-first-one");
        published(first, "Author-first-two");
        String two = published(second, "Author-second");
        published(third, "Author-third");
        jdbc.update("UPDATE sys_user SET nickname='公开昵称',intro='公开简介',email='private@example.test' WHERE user_id=?",
                firstId);
        JsonNode page = data(call("GET", "/blog/authors?pageSize=1", null, null));
        assertThat(page.get("total").asString()).isEqualTo("3");
        assertThat(page.get("totalPages").asString()).isEqualTo("3");
        JsonNode author = page.get("records").get(0);
        assertThat(author.get("userId").asString()).isEqualTo(firstId);
        assertThat(author.get("articleCount").asString()).isEqualTo("2");
        assertThat(author.get("nickname").asString()).isEqualTo("公开昵称");
        assertThat(author.get("intro").asString()).isEqualTo("公开简介");
        assertThat(author.get("lastPublishTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(author.toString()).doesNotContain("username", "email", "password", "role", "lastLogin", "private@");
        assertThat(data(call("GET", "/blog/authors/" + firstId, first, null))).isEqualTo(author);
        assertThat(data(call("GET", "/blog/authors?page=2&pageSize=1", null, null)).get("records").get(0)
                .get("userId").asString()).isEqualTo(userId("discovery_author_second"));
        assertThat(data(call("GET", "/blog/authors?page=9&pageSize=1", null, null)).get("records").isEmpty()).isTrue();
        code(call("GET", "/blog/authors/" + userId("discovery_author_empty"), first, null), 404, "NOT_FOUND");
        code(call("GET", "/blog/authors/9223372036854775807", first, null), 404, "NOT_FOUND");
        code(status(second, two, 2), 200, "OK");
        code(call("GET", "/blog/authors/" + userId("discovery_author_second"), first, null), 404, "NOT_FOUND");
        jdbc.update("UPDATE blog_article SET visibility=2 WHERE author_id=?", firstId);
        code(call("GET", "/blog/authors/" + firstId, first, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/blog/authors", null, null)).get("total").asString()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, one))
                .isZero();
        assertThat(empty).isNotBlank();
    }

    @Test
    void discoveryTaxonomyDetailsCountOnlyPublicAndPreserveCaseAndEmptyResources() throws Exception {
        String token = actor("discovery_taxonomy", 1);
        seedDictionary();
        jdbc.update("INSERT INTO blog_category(category_id,category_name,slug) VALUES(703,'空分类','Empty-Category')");
        String visible = published(token, "Taxonomy-visible");
        String hidden = published(token, "Taxonomy-hidden");
        String draft = create(token, "Taxonomy-draft", "body");
        for (String article : List.of(visible, hidden, draft)) {
            jdbc.update("UPDATE blog_article SET category_id=700 WHERE article_id=?", article);
            jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", article);
        }
        jdbc.update("UPDATE blog_article SET visibility=2 WHERE article_id=?", hidden);
        MvcResult category = call("GET", "/blog/categories/category", null, null);
        code(category, 200, "OK");
        assertThat(data(category).get("articleCount").asString()).isEqualTo("1");
        assertThat(data(category).get("categoryId").asString()).isEqualTo("700");
        assertThat(category.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(call("GET", "/blog/tags/tag-one", token, null)).get("articleCount").asString()).isEqualTo("1");
        assertThat(data(call("GET", "/blog/tags/tag-two", null, null)).get("articleCount").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/categories/Empty-Category", null, null)).get("articleCount").asString())
                .isEqualTo("0");
        for (String path : List.of("/blog/categories/empty-category", "/blog/tags/Tag-one", "/blog/tags/absent")) {
            code(call("GET", path, token, null), 404, "NOT_FOUND");
        }
        assertThat(data(call("GET", "/blog/categories", null, null)).get("records").get(0).has("articleCount")).isFalse();
        assertThat(data(call("GET", "/blog/tags", null, null)).get("records").get(0).has("articleCount")).isFalse();
        jdbc.update("UPDATE blog_category SET status=0 WHERE category_id=700");
        publicCaches.getCache("articleDetail").clear(); publicCaches.getCache("categories").clear();
        code(call("GET", "/blog/categories/category", token, null), 404, "NOT_FOUND");
        jdbc.update("UPDATE blog_tag SET slug='Renamed' WHERE tag_id=701");
        publicCaches.getCache("tags").clear();
        code(call("GET", "/blog/tags/tag-one", null, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/blog/tags/Renamed", null, null)).get("articleCount").asString()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT SUM(view_count) FROM blog_article", Long.class)).isZero();
    }

    @Test
    void discoveryRoutesRejectUndeclaredDuplicateBlankBodiesAndInvalidIdentifiers() throws Exception {
        String token = actor("discovery_input", 1);
        String article = published(token, "Discovery-input");
        seedDictionary();
        for (String path : List.of("/blog/authors", "/me/liked-articles")) {
            for (String query : List.of("page=0", "pageSize=101", "page=1&page=2", "page=", "userId=1", "authorId=1")) {
                code(call("GET", path + "?" + query, token, null), 400, "VALIDATION_ERROR");
            }
        }
        for (String path : List.of("/blog/authors/" + userId("discovery_input"), "/blog/categories/category",
                "/blog/tags/tag-one", "/blog/authors", "/me/liked-articles")) {
            code(call("GET", path, token, "{}"), 400, "VALIDATION_ERROR");
            code(call("GET", path + "?unknown=1", token, null), 400, "VALIDATION_ERROR");
        }
        for (String id : List.of("0", "01", "-1", "1.0", "9223372036854775808")) {
            code(call("GET", "/blog/articles?authorId=" + id, null, null), 400, "VALIDATION_ERROR");
            code(call("GET", "/blog/authors/" + id, null, null), 400, "VALIDATION_ERROR");
        }
        for (String slug : List.of("bad_slug", "-", "a".repeat(101))) {
            code(call("GET", "/blog/categories/" + slug, null, null), 400, "VALIDATION_ERROR");
            code(call("GET", "/blog/tags/" + slug, null, null), 400, "VALIDATION_ERROR");
        }
        code(call("GET", "/blog/articles?authorId=", null, null), 400, "VALIDATION_ERROR");
        code(call("GET", "/blog/articles?authorId=1&authorId=2", null, null), 400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, article))
                .isZero();
    }

    @Test
    void discoveryLikedArticlesAreOwnCompletePublicAndSortedByStableLikeTime() throws Exception {
        String admin = actor("discovery_like_admin", 1);
        String member = actor("discovery_like_member", 3);
        String other = actor("discovery_like_other", 3);
        String first = published(admin, "Discovery-like-first");
        String second = published(admin, "Discovery-like-second");
        String third = published(admin, "Discovery-like-third");
        jdbc.update("UPDATE blog_article SET is_top=1 WHERE article_id=?", first);
        code(call("PUT", likePath(first), member, null), 200, "OK");
        code(call("PUT", likePath(second), member, null), 200, "OK");
        code(call("PUT", likePath(third), other, null), 200, "OK");
        jdbc.update("UPDATE blog_article_like SET create_time='2026-01-01 00:00:00.000' WHERE user_id=?",
                userId("discovery_like_member"));
        MvcResult response = call("GET", "/me/liked-articles?pageSize=1", member, null);
        code(response, 200, "OK");
        assertThat(data(response).get("total").asString()).isEqualTo("2");
        JsonNode record = data(response).get("records").get(0);
        assertThat(record.get("article").get("articleId").asString()).isEqualTo(second);
        assertThat(record.get("likedAt").asString()).isEqualTo("2026-01-01T00:00:00.000Z");
        assertThat(record.get("article").has("tags")).isTrue();
        assertThat(record.get("article").has("commentCount")).isTrue();
        assertThat(record.get("article").toString()).doesNotContain("markdownContent", "password", "authorId");
        code(call("PUT", likePath(first), member, null), 200, "OK");
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("records").get(0).get("article")
                .get("articleId").asString()).isEqualTo(second);
        code(call("DELETE", likePath(first), member, null), 200, "OK");
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("total").asString()).isEqualTo("1");
        code(call("PUT", likePath(first), member, null), 200, "OK");
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("records").get(0).get("article")
                .get("articleId").asString()).isEqualTo(first);
        code(status(admin, first, 2), 200, "OK");
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("total").asString()).isEqualTo("1");
        jdbc.update("UPDATE blog_article SET visibility=2 WHERE article_id=?", second);
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("records").isEmpty()).isTrue();
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("total").asString()).isEqualTo("0");
        code(status(admin, first, 1), 200, "OK");
        assertThat(data(call("GET", "/me/liked-articles", member, null)).get("total").asString()).isEqualTo("1");
        assertThat(data(call("GET", "/me/liked-articles", other, null)).get("records").get(0).get("article")
                .get("articleId").asString()).isEqualTo(third);
        assertThat(jdbc.queryForObject("SELECT SUM(view_count) FROM blog_article", Long.class)).isZero();
    }

    @Test
    void discoveryLikedArticlesRequireCurrentEnabledIdentityWithoutWritePermission() throws Exception {
        code(call("GET", "/me/liked-articles", null, null), 401, "UNAUTHENTICATED");
        String token = actor("discovery_like_identity", 3);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=3 AND permission_id=45");
        code(call("GET", "/me/liked-articles", token, null), 200, "OK");
        code(call("GET", "/me/liked-articles?userId=" + userId("discovery_like_identity"), token, null), 400,
                "VALIDATION_ERROR");
        jdbc.update("UPDATE sys_user SET status=0 WHERE username='discovery_like_identity'");
        code(call("GET", "/me/liked-articles", token, null), 403, "ACCOUNT_DISABLED");
        jdbc.update("UPDATE sys_user SET status=1 WHERE username='discovery_like_identity'");
        sessions.revokeAll(Long.parseLong(userId("discovery_like_identity")));
        code(call("GET", "/me/liked-articles", token, null), 401, "UNAUTHENTICATED");
    }

    @Test
    void discoveryPurgeChecksPermissionsRevisionTrashAndStrictInputBeforeDeleting() throws Exception {
        String token = actor("discovery_purge_input", 1);
        String author = actor("discovery_purge_author", 2);
        String article = published(token, "Purge-input");
        String path = "/admin/articles/" + article + "/permanent";
        String normalVersion = revision(token, article);
        code(write("DELETE", path, token, normalVersion, null), 409, "STATE_CONFLICT");
        code(write("DELETE", "/admin/articles/" + article, token, normalVersion, null), 200, "OK");
        String version = revision(token, article);
        code(call("DELETE", path, null, null), 401, "UNAUTHENTICATED");
        code(call("DELETE", path, author, null), 403, "FORBIDDEN");
        code(call("DELETE", path, token, null), 428, "PRECONDITION_REQUIRED");
        code(write("DELETE", path, token, "invalid", null), 400, "VALIDATION_ERROR");
        code(write("DELETE", path, token, normalVersion, null), 412, "REVISION_CONFLICT");
        code(write("DELETE", path + "?force=true", token, version, null), 400, "VALIDATION_ERROR");
        code(write("DELETE", path, token, version, Map.of()), 400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article WHERE article_id=?", Integer.class, article))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_type='purge' AND result=1",
                Integer.class)).isZero();
        MvcResult result = write("DELETE", path, token, version, null);
        code(result, 200, "OK");
        assertThat(data(result).isNull()).isTrue();
        assertThat(result.getResponse().getHeader("X-Revision")).isNull();
        code(write("DELETE", path, token, version, null), 404, "NOT_FOUND");
    }

    @Test
    void discoveryPurgeRequiresIndependentDeleteAndPurgeScopesAndProtectsFixedPermission() throws Exception {
        String admin = actor("discovery_scope_admin", 1);
        String writer = actor("discovery_scope_writer", 2);
        String other = create(admin, "Purge-scope-other", "body");
        code(write("DELETE", "/admin/articles/" + other, admin, revision(admin, other), null), 200, "OK");
        String otherVersion = revision(admin, other);
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(4,'purger','清理权限','ALL')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,4)", userId("discovery_scope_writer"));
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(4,16)");
        code(write("DELETE", "/admin/articles/" + other + "/permanent", writer, otherVersion, null), 404, "NOT_FOUND");
        // 交换 ALL 与 SELF 后仍不能越过另一项权限的作者范围。
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=4");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(4,13),(2,16)");
        code(write("DELETE", "/admin/articles/" + other + "/permanent", writer, otherVersion, null), 404, "NOT_FOUND");
        String own = create(writer, "Purge-scope-own", "body");
        code(write("DELETE", "/admin/articles/" + own, writer, revision(writer, own), null), 200, "OK");
        String version = revision(writer, own);
        jdbc.update("DELETE FROM sys_role_permission WHERE permission_id=13 AND role_id IN(2,4)");
        code(write("DELETE", "/admin/articles/" + own + "/permanent", writer, version, null), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(2,13)");
        code(write("DELETE", "/admin/articles/" + own + "/permanent", writer, version, null), 200, "OK");
        String permissionVersion = dictionaryRevision(admin, "/admin/permissions/16");
        code(write("DELETE", "/admin/permissions/16", admin, permissionVersion, null), 409, "STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article WHERE article_id=?", Integer.class, other))
                .isEqualTo(1);
    }

    @Test
    void discoveryPurgeDeletesDeepCommentAggregateAndPreservesFilesDictionariesHistoryAndAudit() throws Exception {
        String token = actor("discovery_purge_tree", 1);
        seedDictionary();
        String article = create(token, "Purge-tree", "body");
        String file = uploaded(token, "retained.png");
        Path stored = objectPath(file);
        Map<String, Object> value = body("Purge-tree", "![保留文件](" + context(article, file) + ")");
        value.put("categoryId", "700");
        value.put("tagIds", List.of("701", "702"));
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), value), 200, "OK");
        code(status(token, article, 1), 200, "OK");
        long author = Long.parseLong(userId("discovery_purge_tree"));
        comment(9000, article, author, null, 0);
        long parent = 9000;
        for (long id = 1; id <= 1005; id++) {
            comment(id, article, author, parent, (int) (id % 4));
            parent = id;
        }
        String other = published(token, "Purge-retained");
        comment(10000, other, author, null, 1);
        code(call("PUT", likePath(article), token, null), 200, "OK");
        jdbc.update("INSERT INTO blog_page_view(article_id,site_code,page_path,page_title) VALUES(?,'blog',?,'保留标题')",
                article, "/articles/Purge-tree");
        code(write("DELETE", "/admin/articles/" + article, token, revision(token, article), null), 200, "OK");
        int logs = jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE target_id=? AND result=1",
                Integer.class, article);
        code(write("DELETE", "/admin/articles/" + article + "/permanent", token, revision(token, article), null),
                200, "OK");
        for (String table : List.of("blog_article", "blog_article_content", "blog_article_tag", "blog_article_like",
                "blog_comment")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE article_id=?", Integer.class,
                    article)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE article_id=?", Integer.class, other))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_category", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_tag", Integer.class)).isEqualTo(2);
        assertThat(stored).exists();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_file WHERE file_id=?", Integer.class, file))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view WHERE article_id IS NULL AND "
                + "page_path='/articles/Purge-tree' AND page_title='保留标题'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE target_id=? AND result=1",
                Integer.class, article)).isEqualTo(logs + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_type='purge' AND "
                + "permission_code='blog:article:purge' AND result=1 AND target_id=?", Integer.class, article)).isEqualTo(1);
        code(call("GET", "/blog/articles/Purge-tree", null, null), 404, "NOT_FOUND");
        code(call("GET", context(article, file).substring("/api/v1".length()), null, null), 404, "NOT_FOUND");
        code(write("POST", "/admin/articles/" + article + "/restore", token, "\"" + "0".repeat(64) + "\"", null),
                404, "NOT_FOUND");
        assertThat(call("GET", "/blog/rss.xml", null, null).getResponse().getContentAsString()).doesNotContain("Purge-tree");
        assertThat(call("GET", "/blog/sitemap.xml", null, null).getResponse().getContentAsString()).doesNotContain("Purge-tree");
        assertThat(data(call("GET", "/blog/statistics", null, null)).get("pageViewCount").asString()).isEqualTo("1");
        assertThat(create(token, "Purge-tree", "新文章重用 slug")).isNotEqualTo(article);
        code(call("DELETE", "/admin/files/" + file, token, null), 200, "OK");
    }

    @Test
    void discoveryPurgeRollsBackEveryChildAndPreservesFailureAuditWhenDeleteOrAuditFails() throws Exception {
        String token = actor("discovery_purge_rollback", 1);
        for (String target : List.of("article", "audit")) {
            String article = published(token, "Purge-rollback-" + target);
            long user = Long.parseLong(userId("discovery_purge_rollback"));
            long root = target.equals("article") ? 7001 : 7003;
            comment(root, article, user, null, 0);
            comment(root + 1, article, user, root, 1);
            code(call("PUT", likePath(article), token, null), 200, "OK");
            code(write("DELETE", "/admin/articles/" + article, token, revision(token, article), null), 200, "OK");
            String version = revision(token, article);
            if (target.equals("article")) {
                jdbc.execute("CREATE TRIGGER part7_fail BEFORE DELETE ON blog_article FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated purge failure'");
            } else {
                jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN "
                        + "IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
            }
            try {
                code(write("DELETE", "/admin/articles/" + article + "/permanent", token, version, null), 503,
                        "SERVICE_UNAVAILABLE");
                assertThat(revision(token, article)).isEqualTo(version);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE article_id=?", Integer.class,
                        article)).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=?", Integer.class,
                        article)).isEqualTo(1);
                verify(failures, timeout(3000).atLeastOnce()).write(any(SysOperationLog.class));
            } finally {
                jdbc.execute("DROP TRIGGER part7_fail");
            }
            code(write("DELETE", "/admin/articles/" + article + "/permanent", token, version, null), 200, "OK");
        }
    }

    @Test
    void discoveryPurgeRechecksTokenBothPermissionsScopeAndVersionAfterArticleWait() throws Exception {
        String admin = actor("discovery_purge_wait_admin", 1);
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(4,'purger','清理权限','ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(4,16),(4,13)");
        for (String change : List.of("token", "delete", "purge", "scope", "revision", "state")) {
            String name = "discovery_purge_wait_" + change;
            String writer = actor(name, 2);
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,4)", userId(name));
            String article = create(admin, "Purge-wait-" + change, "body");
            String normalVersion = revision(admin, article);
            code(write("DELETE", "/admin/articles/" + article, writer, normalVersion, null), 200, "OK");
            String version = change.equals("state") ? normalVersion : revision(admin, article);
            MvcResult result = waitingInteraction("blog_article", "SELECT * FROM blog_article WHERE article_id=? FOR UPDATE",
                    article, () -> write("DELETE", "/admin/articles/" + article + "/permanent", writer, version, null),
                    () -> {
                        switch (change) {
                            case "token" -> sessions.revokeAll(Long.parseLong(userId(name)));
                            case "delete" -> jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=13");
                            case "purge" -> jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=16");
                            case "scope" -> jdbc.update("UPDATE sys_role SET data_scope='SELF' WHERE role_id=4");
                            case "revision" -> jdbc.update("UPDATE blog_article SET title='已修改' WHERE article_id=?", article);
                            case "state" -> jdbc.update("UPDATE blog_article SET deleted_at=NULL WHERE article_id=?", article);
                        }
                    }, true);
            int expected = switch (change) {
                case "token" -> 401;
                case "delete", "purge" -> 403;
                case "scope" -> 404;
                case "state" -> 409;
                default -> 412;
            };
            String error = switch (expected) {
                case 401 -> "UNAUTHENTICATED";
                case 403 -> "FORBIDDEN";
                case 404 -> "NOT_FOUND";
                case 409 -> "STATE_CONFLICT";
                default -> "REVISION_CONFLICT";
            };
            code(result, expected, error);
            jdbc.update("UPDATE sys_permission SET status=1 WHERE permission_id IN(13,16)");
            jdbc.update("UPDATE sys_role SET data_scope='ALL' WHERE role_id=4");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article WHERE article_id=?", Integer.class, article))
                    .isEqualTo(1);
        }
        assertThat(admin).isNotBlank();
    }

    @Test
    void discoveryPurgeRechecksOriginalTokenAfterUserFileTagAndCommentLocks() throws Exception {
        seedDictionary();
        for (String stage : List.of("user", "file", "tag", "comment")) {
            String name = "discovery_purge_lock_" + stage;
            String token = actor(name, 1);
            String article = create(token, "Purge-lock-" + stage, "body");
            String user = userId(name);
            String table;
            String sql;
            Object key;
            switch (stage) {
                case "user" -> {
                    table = "sys_user";
                    sql = "SELECT * FROM sys_user WHERE user_id=? FOR UPDATE";
                    key = user;
                }
                case "file" -> {
                    addFile(9001, user, "purge-lock.png");
                    jdbc.update("UPDATE blog_article_content SET markdown_content=? WHERE article_id=?",
                            "![锁](" + context(article, "9001") + ")", article);
                    table = "blog_file";
                    sql = "SELECT * FROM blog_file WHERE file_id=? FOR UPDATE";
                    key = 9001;
                }
                case "tag" -> {
                    jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", article);
                    table = "blog_tag";
                    sql = "SELECT * FROM blog_tag WHERE tag_id=? FOR UPDATE";
                    key = 701;
                }
                default -> {
                    comment(9002, article, Long.parseLong(user), null, 1);
                    table = "blog_comment";
                    sql = "SELECT * FROM blog_comment WHERE comment_id=? FOR UPDATE";
                    key = 9002;
                }
            }
            code(write("DELETE", "/admin/articles/" + article, token, revision(token, article), null), 200, "OK");
            String version = revision(token, article);
            MvcResult response = waitingInteraction(table, sql, key,
                    () -> write("DELETE", "/admin/articles/" + article + "/permanent", token, version, null),
                    () -> sessions.revokeAll(Long.parseLong(user)), true);
            code(response, 401, "UNAUTHENTICATED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article WHERE article_id=?", Integer.class, article))
                    .isEqualTo(1);
        }
    }

    @Test
    void discoveryPurgePermissionMigrationIsRepeatableAndDoesNotGrantAuthorsOrMembers() throws Exception {
        Path migration = Path.of("../../sql/migrations/20261031_article_purge_permission.sql");
        jdbc.update("DELETE FROM sys_menu WHERE menu_code='blog:article:purge'");
        jdbc.update("DELETE FROM sys_permission WHERE permission_code='blog:article:purge'");
        jdbc.update("INSERT INTO sys_permission(permission_id,permission_code,permission_name,module) "
                + "VALUES(16,'custom:existing:action','已有权限','custom')");
        try {
            for (int i = 0; i < 2; i++) {
                try (var connection = jdbc.getDataSource().getConnection()) {
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                            new org.springframework.core.io.FileSystemResource(migration));
                }
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code='blog:article:purge'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT permission_id FROM sys_permission WHERE permission_code='blog:article:purge'",
                    Long.class)).isNotEqualTo(16L);
            assertThat(jdbc.queryForObject("SELECT permission_code FROM sys_permission WHERE permission_id=16", String.class))
                    .isEqualTo("custom:existing:action");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_code='blog:article:purge'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT r.role_id FROM sys_role_permission r JOIN sys_permission p ON "
                    + "p.permission_id=r.permission_id WHERE p.permission_code='blog:article:purge'", Integer.class))
                    .containsExactly(1);
        } finally {
            jdbc.update("DELETE FROM sys_menu WHERE menu_code='blog:article:purge'");
            jdbc.update("DELETE FROM sys_permission WHERE permission_code IN('blog:article:purge','custom:existing:action')");
            jdbc.update("INSERT INTO sys_permission(permission_id,permission_code,permission_name,module) "
                    + "VALUES(16,'blog:article:purge','永久删除回收站文章（无法恢复）','blog')");
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(1,16)");
            jdbc.update("INSERT INTO sys_menu(menu_id,parent_id,menu_code,menu_name,menu_type,permission_id,sort_order,is_visible) "
                    + "VALUES(1016,110,'blog:article:purge','永久删除回收站文章（无法恢复）','B',16,16,0)");
            jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,1016)");
        }
    }

    @Test
    void cacheRealHitsUseFiniteTtlAndNormalizedKeysForEveryPublicDomain() throws Exception {
        String token = actor("cache_domains", 1);
        seedDictionary();
        String article = published(token, "Cache-domains");
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("userId", userId("cache_domains")); home.put("nickname", "公开主人"); home.put("tagline", "");
        for (String field : List.of("description", "avatar", "website", "publicEmail", "githubUrl", "location")) { home.put(field, null); }
        code(write("PUT", "/admin/home/profile", token,
                call("GET", "/admin/home/profile", token, null).getResponse().getHeader("X-Revision"), home), 200, "OK");
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("home", "/home/profile"); routes.put("social", "/home/social-links");
        routes.put("experiences", "/cv/experiences"); routes.put("skills", "/cv/skills");
        routes.put("links", "/blog/links"); routes.put("music", "/blog/music"); routes.put("configs", "/blog/configs");
        routes.put("categories", "/blog/categories"); routes.put("tags", "/blog/tags");
        routes.put("authors", "/blog/authors"); routes.put("articles", "/blog/articles");
        routes.put("search", "/blog/articles?keyword=Cache"); routes.put("articleDetail", "/blog/articles/Cache-domains");
        routes.put("archives", "/blog/archives"); routes.put("rankings", "/blog/article-rankings");
        routes.put("comments", "/blog/articles/" + article + "/comments"); routes.put("statistics", "/blog/statistics");
        routes.put("rss", "/blog/rss.xml"); routes.put("sitemap", "/blog/sitemap.xml");
        for (var entry : routes.entrySet()) {
            MvcResult first = call("GET", entry.getValue(), null, null);
            assertThat(first.getResponse().getStatus()).as(entry.getKey()).isEqualTo(200);
            String key = cacheDataKey(entry.getKey());
            long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
            int expected = List.of("home", "social", "experiences", "skills", "links", "music", "configs").contains(entry.getKey()) ? 3600
                    : entry.getKey().equals("articles") ? 600 : entry.getKey().equals("articleDetail") ? 900
                    : List.of("search", "rankings").contains(entry.getKey()) ? 60 : entry.getKey().equals("comments") ? 30
                    : entry.getKey().equals("statistics") ? 300 : 1800;
            assertThat(ttl).as(entry.getKey()).isBetween(expected * 1000L - 5000, expected * 1000L);
            Map<String, Integer> before = new LinkedHashMap<>();
            cacheProbe.calls.forEach((name, count) -> before.put(name, count.get()));
            call("GET", entry.getValue(), null, null);
            assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(ttl);
            String cachedQuery = switch (entry.getKey()) {
                case "articles", "search" -> "com.xuan.mapper.ArticleMapper.articlePage";
                case "articleDetail" -> "com.xuan.mapper.ArticleMapper.adjacent";
                case "comments" -> "com.xuan.mapper.CommentMapper.publicPage";
                default -> null;
            };
            if (cachedQuery != null) { assertThat(cacheProbe.count(cachedQuery)).as(entry.getKey()).isEqualTo(before.getOrDefault(cachedQuery, 0)); }
            if (!List.of("articles", "search", "articleDetail", "comments").contains(entry.getKey())) {
                before.forEach((name, count) -> {
                    if (!name.startsWith("com.xuan.mapper.UserMapper") && !name.startsWith("com.xuan.mapper.AuthorizationMapper")) {
                        assertThat(cacheProbe.count(name)).as(entry.getKey() + " " + name).isEqualTo(count);
                    }
                });
            }
        }
        String articleKey = cacheDataKey("articles");
        code(call("GET", "/blog/articles?page=1&pageSize=10", null, null), 200, "OK");
        assertThat(cacheDataKey("articles")).isEqualTo(articleKey);
        for (String query : List.of("page=11", "pageSize=21", "keyword=Cache&page=4")) {
            int before = cacheProbe.calls.entrySet().stream().filter(e -> e.getKey().startsWith("com.xuan.mapper.ArticleMapper.articlePage")).mapToInt(e -> e.getValue().get()).sum();
            call("GET", "/blog/articles?" + query, null, null); call("GET", "/blog/articles?" + query, null, null);
            int after = cacheProbe.calls.entrySet().stream().filter(e -> e.getKey().startsWith("com.xuan.mapper.ArticleMapper.articlePage")).mapToInt(e -> e.getValue().get()).sum();
            assertThat(after).isGreaterThanOrEqualTo(before + 2);
        }
        String expired = cacheDataKey("social");
        redis.expire(expired, java.time.Duration.ofMillis(1));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (Boolean.TRUE.equals(redis.hasKey(expired)) && System.nanoTime() < deadline) { Thread.onSpinWait(); }
        int socialQueries = cacheProbe.count("com.xuan.mapper.SocialMapper.publicItems");
        call("GET", "/home/social-links", null, null);
        assertThat(cacheProbe.count("com.xuan.mapper.SocialMapper.publicItems")).isEqualTo(socialQueries + 1);
    }

    @Test
    void cacheAssociatedArticleTaxonomyAuthorAndXmlWritesRemoveOldPublicResults() throws Exception {
        String token = actor("cache_associations", 1);
        seedDictionary();
        Map<String, Object> request = body("Cache-associations", "正文");
        request.put("categoryId", "700"); request.put("tagIds", List.of("701"));
        MvcResult created = call("POST", "/admin/articles", token, json.writeValueAsString(request)); code(created, 201, "OK");
        String article = data(created).get("articleId").asString(); code(status(token, article, 1), 200, "OK");
        var beforePrivate = redis.keys("dl:article-test:cache:public:*");
        for (String path : List.of("/me", "/me/liked-articles", "/admin/articles/" + article, "/blog/messages")) {
            code(call("GET", path, token, null), 200, "OK");
        }
        assertThat(redis.keys("dl:article-test:cache:public:*")).isEqualTo(beforePrivate);
        List<String> warm = List.of("/blog/articles", "/blog/articles?keyword=Cache", "/blog/articles/Cache-associations", "/blog/archives",
                "/blog/article-rankings", "/blog/categories", "/blog/categories/category", "/blog/tags", "/blog/tags/tag-one",
                "/blog/authors", "/blog/authors/" + userId("cache_associations"), "/blog/statistics", "/blog/rss.xml", "/blog/sitemap.xml", "/blog/sitemap.xml?page=1",
                "/blog/articles/" + article + "/comments");
        for (String path : warm) { assertThat(call("GET", path, null, null).getResponse().getStatus()).as(path).isEqualTo(200); }
        request.put("slug", "Cache-renamed"); request.put("title", "Renamed title");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), request), 200, "OK");
        code(call("GET", "/blog/articles/Cache-associations", null, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/blog/articles?keyword=Cache", null, null)).get("total").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/articles/Cache-renamed", null, null)).get("title").asString()).isEqualTo("Renamed title");
        for (String path : List.of("/blog/rss.xml", "/blog/sitemap.xml", "/blog/sitemap.xml?page=1")) {
            assertThat(call("GET", path, null, null).getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Cache-renamed").doesNotContain("Cache-associations");
        }
        String categoryRevision = call("GET", "/admin/categories/700", token, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/admin/categories/700", token, categoryRevision, categoryBody("renamed category", "Renamed-category", null, 1, 1)), 200, "OK");
        code(call("GET", "/blog/categories/category", null, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/blog/articles/Cache-renamed", null, null)).get("category").get("slug").asString()).isEqualTo("Renamed-category");
        code(write("DELETE", "/admin/tags/701", token, call("GET", "/admin/tags/701", token, null).getResponse().getHeader("X-Revision"), null), 200, "OK");
        code(call("GET", "/blog/tags/tag-one", null, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/blog/articles/Cache-renamed", null, null)).get("tags").isEmpty()).isTrue();
        String meVersion = call("GET", "/me", token, null).getResponse().getHeader("X-Revision");
        code(write("PATCH", "/me/profile", token, meVersion, Map.of("nickname", "New public author")), 200, "OK");
        assertThat(data(call("GET", "/blog/authors", null, null)).get("records").get(0).get("nickname").asString()).isEqualTo("New public author");
        assertThat(data(call("GET", "/blog/articles/Cache-renamed", null, null)).get("author").get("nickname").asString()).isEqualTo("New public author");
        // 热缓存失败仍回滚阅读，不发布事务中的候选片段。
        long views = jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, article);
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE UPDATE ON blog_article FOR EACH ROW BEGIN IF NEW.view_count > OLD.view_count THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reading rollback'; END IF; END");
        code(call("GET", "/blog/articles/Cache-renamed", null, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, article)).isEqualTo(views);
        jdbc.execute("DROP TRIGGER part7_fail");
        code(status(token, article, 2), 200, "OK");
        code(call("GET", "/blog/articles/Cache-renamed", token, null), 404, "NOT_FOUND");
        assertThat(data(call("GET", "/blog/articles", token, null)).get("total").asString()).isEqualTo("0");
        for (String path : List.of("/blog/rss.xml", "/blog/sitemap.xml?page=1")) {
            assertThat(call("GET", path, null, null).getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Cache-renamed");
        }
        code(write("DELETE", "/admin/articles/" + article, token, revision(token, article), null), 200, "OK");
        code(write("POST", "/admin/articles/" + article + "/restore", token, revision(token, article), null), 200, "OK");
        code(call("GET", "/blog/articles/Cache-renamed", null, null), 404, "NOT_FOUND");
        code(write("DELETE", "/admin/articles/" + article, token, revision(token, article), null), 200, "OK");
        code(write("DELETE", "/admin/articles/" + article + "/permanent", token, revision(token, article), null), 200, "OK");
        code(call("GET", "/blog/articles/" + article + "/comments", null, null), 404, "NOT_FOUND");
    }

    @Test
    void cacheSocialCreateUpdateDeleteInvalidateEmptyAndWarmResponses() throws Exception {
        String token = actor("cache_social", 1);
        assertThat(data(call("GET", "/home/social-links", null, null)).size()).isZero();
        MvcResult created = call("POST", "/admin/home/social-links", token, json.writeValueAsString(cacheSocial("old")));
        code(created, 201, "OK");
        String path = "/admin/home/social-links/" + data(created).get("socialId").asString();
        assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("old");
        code(write("PUT", path, token, created.getResponse().getHeader("X-Revision"), cacheSocial("new")), 200, "OK");
        assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("new");
        code(write("DELETE", path, token, call("GET", path, token, null).getResponse().getHeader("X-Revision"), null), 200, "OK");
        assertThat(data(call("GET", "/home/social-links", null, null)).size()).isZero();
        assertThat(redis.opsForSet().size(publicCacheStore.prefix("social") + "writers")).isZero();
    }

    @Test
    void cacheOldDatabaseSnapshotCannotRefillAfterCommittedWrite() throws Exception {
        String token = actor("cache_old_snapshot", 1);
        MvcResult created = call("POST", "/admin/home/social-links", token, json.writeValueAsString(cacheSocial("old")));
        String path = "/admin/home/social-links/" + data(created).get("socialId").asString();
        cacheProbe.pause("com.xuan.mapper.SocialMapper.publicItems");
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<MvcResult> old = executor.submit(() -> call("GET", "/home/social-links", null, null));
            try {
                assertThat(cacheProbe.reached.await(5, TimeUnit.SECONDS)).isTrue();
                code(write("PUT", path, token, created.getResponse().getHeader("X-Revision"), cacheSocial("new")), 200, "OK");
            } finally { cacheProbe.release.countDown(); }
            assertThat(data(old.get(5, TimeUnit.SECONDS)).get(0).get("name").asString()).isEqualTo("old");
        }
        assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("new");
        assertThat(redis.opsForValue().get(cacheDataKey("social"))).contains("new").doesNotContain("old");
    }

    @Test
    void cacheWriteProtectionBypassesWarmValuesUntilCommitAndPreservesOtherWriters() throws Exception {
        String token = actor("cache_write_guard", 1);
        MvcResult created = call("POST", "/admin/home/social-links", token, json.writeValueAsString(cacheSocial("old")));
        String path = "/admin/home/social-links/" + data(created).get("socialId").asString();
        call("GET", "/home/social-links", null, null);
        cacheProbe.pause("com.xuan.mapper.SocialMapper.replace");
        String writers = publicCacheStore.prefix("social") + "writers";
        redis.opsForSet().add(writers, "orphan-from-ended-process");
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<MvcResult> write = executor.submit(() -> write("PUT", path, token, created.getResponse().getHeader("X-Revision"), cacheSocial("new")));
            try {
                assertThat(cacheProbe.reached.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(redis.opsForSet().size(writers)).isEqualTo(2);
                int queries = cacheProbe.count("com.xuan.mapper.SocialMapper.publicItems");
                assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("old");
                assertThat(cacheProbe.count("com.xuan.mapper.SocialMapper.publicItems")).isEqualTo(queries + 1);
            } finally { cacheProbe.release.countDown(); }
            code(write.get(5, TimeUnit.SECONDS), 200, "OK");
        }
        assertThat(redis.opsForSet().members(writers)).containsExactly("orphan-from-ended-process");
        assertThat(redis.getExpire(writers)).isEqualTo(-1);
        assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("new");
        // 隔离测试已经确认模拟的进程事务结束，先轮换再清遗留保护。
        publicCaches.getCache("social").clear(); redis.opsForSet().remove(writers, "orphan-from-ended-process");
    }

    @Test
    void cacheConcurrentWritersOnlyRemoveTheirOwnProtection() throws Exception {
        String first = actor("cache_parallel_first", 1);
        String second = actor("cache_parallel_second", 1);
        MvcResult a = call("POST", "/admin/home/social-links", first, json.writeValueAsString(cacheSocial("first")));
        MvcResult b = call("POST", "/admin/home/social-links", second, json.writeValueAsString(cacheSocial("second")));
        var gates = cacheProbe.pauseTwo("com.xuan.mapper.SocialMapper.replace");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> one = executor.submit(() -> write("PUT", "/admin/home/social-links/" + data(a).get("socialId").asString(), first,
                    a.getResponse().getHeader("X-Revision"), cacheSocial("first-updated")));
            Future<MvcResult> two = null;
            try {
                assertThat(gates.get(0).reached.await(5, TimeUnit.SECONDS)).isTrue();
                two = executor.submit(() -> write("PUT", "/admin/home/social-links/" + data(b).get("socialId").asString(), second,
                        b.getResponse().getHeader("X-Revision"), cacheSocial("second-updated")));
                assertThat(gates.get(1).reached.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(redis.opsForSet().size(publicCacheStore.prefix("social") + "writers")).isEqualTo(2);
                gates.get(0).release.countDown(); code(one.get(5, TimeUnit.SECONDS), 200, "OK");
                assertThat(redis.opsForSet().size(publicCacheStore.prefix("social") + "writers")).isEqualTo(1);
                assertThat(data(call("GET", "/home/social-links", null, null)).toString()).contains("first-updated").doesNotContain("second-updated");
            } finally { gates.forEach(gate -> gate.release.countDown()); }
            code(two.get(5, TimeUnit.SECONDS), 200, "OK");
        }
        assertThat(redis.opsForSet().size(publicCacheStore.prefix("social") + "writers")).isZero();
        assertThat(data(call("GET", "/home/social-links", null, null)).toString()).contains("first-updated", "second-updated");
    }

    @Test
    void cacheRegistrationFailureRollsBackAndPostCommitFailureKeepsProtection() throws Exception {
        String token = actor("cache_failure", 1);
        String writers = publicCacheStore.prefix("social") + "writers";
        redis.opsForValue().set(writers, "broken-redis-type");
        code(call("POST", "/admin/home/social-links", token, json.writeValueAsString(cacheSocial("rejected"))), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM home_social_link", Integer.class)).isZero();
        redis.delete(writers);
        MvcResult created = call("POST", "/admin/home/social-links", token, json.writeValueAsString(cacheSocial("old")));
        String path = "/admin/home/social-links/" + data(created).get("socialId").asString();
        call("GET", "/home/social-links", null, null);
        cacheProbe.pause("com.xuan.mapper.SocialMapper.replace");
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<MvcResult> update = executor.submit(() -> write("PUT", path, token, created.getResponse().getHeader("X-Revision"), cacheSocial("committed")));
            try {
                assertThat(cacheProbe.reached.await(5, TimeUnit.SECONDS)).isTrue();
                redis.opsForValue().set(writers, "broken-after-registration");
            } finally { cacheProbe.release.countDown(); }
            code(update.get(5, TimeUnit.SECONDS), 503, "SERVICE_UNAVAILABLE");
        }
        assertThat(jdbc.queryForObject("SELECT name FROM home_social_link", String.class)).isEqualTo("committed");
        assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("committed");
        // 恢复后的旧数据键存在也不会命中；只有确认事务结束后才手工轮换并移除错误保护。
        redis.delete(writers); publicCaches.getCache("social").clear();
        call("GET", "/home/social-links", null, null);
        redis.opsForValue().set(cacheDataKey("social"), "incompatible-json");
        assertThat(data(call("GET", "/home/social-links", null, null)).get(0).get("name").asString()).isEqualTo("committed");
    }

    @Test
    void cacheReadRollbackDoesNotPublishValuesAndWriteRollbackReleasesOwnProtection() throws Exception {
        String token = actor("cache_rollback", 1);
        call("GET", "/home/social-links", null, null);
        String generation = redis.opsForValue().get(publicCacheStore.prefix("social") + "generation");
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fail audit'; END IF; END");
        code(call("POST", "/admin/home/social-links", token, json.writeValueAsString(cacheSocial("rolled-back"))), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM home_social_link", Integer.class)).isZero();
        assertThat(redis.opsForSet().size(publicCacheStore.prefix("social") + "writers")).isZero();
        assertThat(redis.opsForValue().get(publicCacheStore.prefix("social") + "generation")).isEqualTo(generation);
        assertThat(data(call("GET", "/home/social-links", null, null)).size()).isZero();
        publicCaches.getCache("social").clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { publicSocials.publicItems(); tx.setRollbackOnly(); });
        assertThat(cacheDataKeyOrNull("social")).isNull();
    }

    @Test
    void ratePublicGetAndDedicatedBudgetsApplyOnHotCacheAcrossParameters() throws Exception {
        for (int n = 0; n < 120; n++) {
            code(call("GET", n % 2 == 0 ? "/home/social-links" : "/cv/skills?page=" + (n + 1), null, null), 200, "OK");
        }
        MvcResult rejected = call("GET", "/blog/configs", null, null);
        code(rejected, 429, "RATE_LIMITED"); assertThat(Integer.parseInt(rejected.getResponse().getHeader("Retry-After"))).isPositive();
        redis.delete(redis.keys("dl:article-test:rate:interface:public-*"));
        for (int n = 0; n < 10; n++) { code(call("GET", "/blog/articles?keyword=search" + n + "&page=" + (n + 1), null, null), 200, "OK"); }
        code(call("GET", "/blog/articles?keyword=another", null, null), 429, "RATE_LIMITED");
        assertThat(data(call("GET", "/blog/articles", null, null)).get("total").asString()).isEqualTo("0");
        for (int n = 0; n < 30; n++) { assertThat(call("GET", n % 2 == 0 ? "/blog/rss.xml" : "/blog/sitemap.xml?page=1", null, null).getResponse().getStatus()).isEqualTo(200); }
        code(call("GET", "/blog/sitemap.xml?page=2", null, null), 429, "RATE_LIMITED");
    }

    @Test
    void rateInvalidPublicParametersAreRejectedBeforeConsumingBudget() throws Exception {
        for (String path : List.of("/blog/articles?categoryId=01", "/blog/articles?tagId=0", "/blog/articles?authorId=9223372036854775808",
                "/blog/articles/1/comments?parentId=01", "/blog/messages?parentId=0", "/blog/authors/01", "/blog/articles?page=0")) {
            code(call("GET", path, null, null), 400, "VALIDATION_ERROR");
        }
        assertThat(redis.keys("dl:article-test:rate:interface:public-*")).isEmpty();
    }

    @Test
    void rateLoginSuccessAndFailureConsumeTotalBudgetBeforePasswordWork() throws Exception {
        String token = actor("rate_login_total", 1);
        for (int n = 1; n < 30; n++) {
            code(call("POST", "/auth/login", null, json.writeValueAsString(Map.of("username", "rate_login_total", "password", PASSWORD))), 200, "OK");
        }
        String last = jdbc.queryForObject("SELECT CAST(last_login_time AS CHAR) FROM sys_user WHERE username='rate_login_total'", String.class);
        code(call("POST", "/auth/login", null, json.writeValueAsString(Map.of("username", "rate_login_total", "password", PASSWORD))), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT CAST(last_login_time AS CHAR) FROM sys_user WHERE username='rate_login_total'", String.class)).isEqualTo(last);
        assertThat(redis.keys("dl:article-test:rate:login:*")).isEmpty();
        assertThat(token).isNotBlank();
    }

    @Test
    void rateAnalyticsDimensionsCannotBeResetByFingerprintOrIpAndRejectBeforeWrites() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fingerprint", "rate-fingerprint-00000001"); body.put("siteCode", "blog"); body.put("pagePath", "/");
        body.put("pageTitle", null); body.put("referer", null); body.put("articleId", null);
        for (int n = 0; n < 30; n++) { assertThat(track(body).getResponse().getStatus()).isEqualTo(200); }
        code(track(body), 429, "RATE_LIMITED");
        body.put("fingerprint", "rate-fingerprint-00000002");
        for (int n = 0; n < 29; n++) { assertThat(track(body).getResponse().getStatus()).isEqualTo(200); }
        body.put("fingerprint", "rate-fingerprint-00000003");
        code(track(body), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isEqualTo(59);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_visitor", Integer.class)).isEqualTo(2);
    }

    @Test
    void rateMessageEditsShareCreationBudgetAndAdminRepliesHaveIndependentBudget() throws Exception {
        String admin = actor("rate_message_admin", 1);
        String member = actor("rate_message_member", 3);
        String id = createMessage(member, null, "message", 0);
        reviewMessage(admin, id, 1);
        for (int n = 0; n < 9; n++) {
            String revision = call("GET", "/me/messages/" + id, member, null).getResponse().getHeader("X-Revision");
            code(write("PUT", "/me/messages/" + id, member, revision, messageEdit("edit-" + n)), 200, "OK");
        }
        String revision = call("GET", "/me/messages/" + id, member, null).getResponse().getHeader("X-Revision");
        code(write("PUT", "/me/messages/" + id, member, revision, messageEdit("rejected")), 429, "RATE_LIMITED");
        code(call("POST", "/blog/messages", member, json.writeValueAsString(messageBody(null, "rejected", 0))), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message", Integer.class)).isEqualTo(1);
        reviewMessage(admin, id, 1);
        for (int n = 0; n < 10; n++) { code(call("POST", "/admin/messages/" + id + "/replies", admin, json.writeValueAsString(messageEdit("reply-" + n))), 201, "OK"); }
        code(call("POST", "/admin/messages/999999/replies", admin, json.writeValueAsString(messageEdit("rejected"))), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message", Integer.class)).isEqualTo(11);
    }

    @Test
    void rateSubscriptionConfirmAndLinkUnsubscribeRejectWithoutConsumingValidTicket() throws Exception {
        String admin = actor("rate_subscription_admin", 1);
        String member = actor("rate_subscription_member", 3);
        String ticket = requestSubscription(member, "rate-subscribe@example.test");
        for (int n = 0; n < 10; n++) { code(confirm(member, com.xuan.utils.TokenUtils.random()), 400, "SUBSCRIPTION_TICKET_INVALID"); }
        code(confirm(member, ticket), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT verified_at IS NULL FROM blog_rss_subscription", Boolean.class)).isTrue();
        redis.delete(redis.keys("dl:article-test:rate:interface:subscription-confirm:*"));
        code(confirm(member, ticket), 200, "OK");
        published(admin, "Rate-subscription-article"); finishAudits();
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("rate-subscribe@example.test"), eq("新文章发布"), text.capture());
        String unsubscribeTicket = fragmentTicket(text.getValue(), "unsubscribeTicket");
        for (int n = 0; n < 30; n++) { code(unsubscribe(com.xuan.utils.TokenUtils.random()), 400, "SUBSCRIPTION_TICKET_INVALID"); }
        code(unsubscribe(unsubscribeTicket), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT unsubscribe_time IS NULL FROM blog_rss_subscription", Boolean.class)).isTrue();
        redis.delete(redis.keys("dl:article-test:rate:interface:subscription-unsubscribe:*"));
        code(unsubscribe(unsubscribeTicket), 200, "OK"); code(unsubscribe(unsubscribeTicket), 200, "OK");
    }

    @Test
    void rateReportsShareAllFivePathsAndDetailsAndFilesUseIndependentBudgets() throws Exception {
        String admin = actor("rate_report_admin", 1);
        String article = published(admin, "Rate-detail-article");
        List<String> reports = List.of("overview", "page-views", "visitors", "provinces", "articles");
        for (int n = 0; n < 30; n++) { code(call("GET", "/admin/statistics/" + reports.get(n % 5) + "?begin=2026-01-01&end=2026-01-" + String.format("%02d", n % 28 + 1), admin, null), 200, "OK"); }
        code(call("GET", "/admin/statistics/overview?begin=2025-01-01&end=2025-01-01", admin, null), 429, "RATE_LIMITED");
        for (int n = 0; n < 60; n++) { code(call("GET", "/blog/articles/Rate-detail-article", null, null), 200, "OK"); }
        code(call("GET", "/blog/articles/Another-slug", null, null), 429, "RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, article)).isEqualTo(60);
        for (int n = 0; n < 120; n++) { code(call("GET", "/blog/articles/" + article + "/files/" + (n + 1) + "/content", null, null), 404, "NOT_FOUND"); }
        code(call("GET", "/blog/articles/" + article + "/files/999999/content", null, null), 429, "RATE_LIMITED");
        // 文件预算独立于正文公共预算；安全 Redis 错误仍在进入阅读事务前明确拒绝。
        String budget = redis.keys("dl:article-test:rate:interface:public-get:*").iterator().next();
        redis.delete(budget); redis.opsForSet().add(budget, "wrong-type");
        code(call("GET", "/blog/articles/Rate-detail-article", null, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, article)).isEqualTo(60);
    }

    @Test
    void rateRedisAtomicWindowsAreSharedAcrossInstancesAndIndependentAcrossDimensions() throws Exception {
        var one = new com.xuan.ratelimit.RedisRateLimiter(redis);
        var two = new com.xuan.ratelimit.RedisRateLimiter(redis);
        String ip = "dl:article-test:rate:atomic:ip";
        String user = "dl:article-test:rate:atomic:user";
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int n = 0; n < 80; n++) {
                var limiter = n % 2 == 0 ? one : two;
                work.add(() -> { try { limiter.hit(List.of(ip, user), 60, List.of(30, 10)); return true; }
                    catch (com.xuan.exception.BusinessException rejected) { assertThat(rejected.getStatus()).isEqualTo(429); return false; } });
            }
            int accepted = 0;
            for (Future<Boolean> result : executor.invokeAll(work)) { if (result.get()) { accepted++; } }
            assertThat(accepted).isEqualTo(10);
        }
        long ttl = redis.getExpire(ip, TimeUnit.MILLISECONDS);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> one.hit(List.of(ip, user + "-new"), 60, List.of(30, 10))).isInstanceOf(com.xuan.exception.BusinessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> two.hit(List.of(ip + "-new", user), 60, List.of(30, 10))).isInstanceOf(com.xuan.exception.BusinessException.class);
        assertThat(redis.getExpire(ip, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(ttl);
        assertThat(redis.opsForValue().get(ip)).isEqualTo("81");
        redis.expire(ip, java.time.Duration.ofMillis(1)); redis.expire(user, java.time.Duration.ofMillis(1));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((Boolean.TRUE.equals(redis.hasKey(ip)) || Boolean.TRUE.equals(redis.hasKey(user))) && System.nanoTime() < deadline) { Thread.onSpinWait(); }
        one.hit(List.of(ip, user), 60, List.of(30, 10));
        assertThat(redis.opsForValue().get(ip)).isEqualTo("1");
    }

    private MvcResult track(Map<String, Object> body) throws Exception {
        return mvc.perform(req("POST", "/analytics/page-views").header("Origin", ORIGIN).contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
    }
    private Map<String, Object> cacheSocial(String name) {
        return Map.of("name", name, "icon", "github", "url", "https://example.test/social", "sortOrder", 0, "status", 1);
    }
    private String cacheDataKey(String domain) {
        String key = cacheDataKeyOrNull(domain); assertThat(key).as(domain).isNotNull(); return key;
    }
    private String cacheDataKeyOrNull(String domain) {
        String prefix = publicCacheStore.prefix(domain);
        String generation = redis.opsForValue().get(prefix + "generation");
        return redis.keys(prefix + generation + ":*").stream().findFirst().orElse(null);
    }

    private Map<String, Object> body(String slug, String markdown) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", slug);
        value.put("slug", slug);
        value.put("summary", "");
        value.put("categoryId", null);
        value.put("coverUrl", null);
        value.put("articleType", 1);
        value.put("originalUrl", null);
        value.put("visibility", 1);
        value.put("isTop", 0);
        value.put("allowComment", 1);
        value.put("markdownContent", markdown);
        value.put("tagIds", List.of());
        return value;
    }

    private String actor(String username, int role) throws Exception {
        MvcResult registered = call("POST", "/auth/register", null,
                json.writeValueAsString(Map.of("username", username, "password", PASSWORD, "nickname", "叶")));
        code(registered, 201, "OK");
        if (role != 3) {
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",
                    data(registered).get("userId").asString(), role);
        }
        MvcResult logged = call("POST", "/auth/login", null,
                json.writeValueAsString(Map.of("username", username, "password", PASSWORD)));
        code(logged, 200, "OK");
        return data(logged).get("accessToken").asString();
    }

    private String userId(String username) {
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=?", String.class, username);
    }

    private String create(String token, String slug, String markdown) throws Exception {
        MvcResult result = call("POST", "/admin/articles", token, json.writeValueAsString(body(slug, markdown)));
        code(result, 201, "OK");
        return data(result).get("articleId").asString();
    }

    private String revision(String token, String id) throws Exception {
        MvcResult result = call("GET", "/admin/articles/" + id, token, null);
        code(result, 200, "OK");
        return result.getResponse().getHeader("X-Revision");
    }

    private MvcResult status(String token, String id, int state) throws Exception {
        return write("PUT", "/admin/articles/" + id + "/status", token, revision(token, id), Map.of("status", state));
    }

    private MvcResult call(String method, String path, String token, String body) throws Exception {
        Map<String, String> generations = cacheWriteSnapshot(method, path);
        var builder = req(method, path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType("application/json").content(body);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        if (result.getResponse().getStatus() >= 200 && result.getResponse().getStatus() < 300) {
            generations.forEach((domain, before) -> assertThat(redis.opsForValue().get(publicCacheStore.prefix(domain) + "generation"))
                    .as(method + " " + path + " 应在真实提交后失效 " + domain).isNotEqualTo(before));
        }
        return result;
    }

    private MvcResult write(String method, String path, String token, String revision, Object body) throws Exception {
        Map<String, String> generations = cacheWriteSnapshot(method, path);
        var builder = req(method, path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (revision != null) {
            builder.header("If-Revision", revision);
        }
        if (body != null) {
            builder.contentType("application/json").content(json.writeValueAsString(body));
        }
        MvcResult result = mvc.perform(builder).andReturn();
        if (result.getResponse().getStatus() >= 200 && result.getResponse().getStatus() < 300) {
            generations.forEach((domain, before) -> assertThat(redis.opsForValue().get(publicCacheStore.prefix(domain) + "generation"))
                    .as(method + " " + path + " 应在真实提交后失效 " + domain).isNotEqualTo(before));
        }
        return result;
    }

    private Map<String, String> cacheWriteSnapshot(String method, String path) {
        List<String> domains = List.of();
        if (method.equals("GET")) { return Map.of(); }
        List<String> article = List.of("articles", "search", "articleDetail", "archives", "rankings", "categories", "tags", "authors", "statistics", "rss", "sitemap", "comments");
        if (path.matches("/admin/articles(?:/.*)?")) { domains = article; }
        else if (path.matches("/admin/(categories|tags)(?:/.*)?")) {
            String other = path.startsWith("/admin/categories") ? "tags" : "categories";
            domains = article.stream().filter(d -> !d.equals(other) && !d.equals("comments")).toList();
        } else if (path.equals("/me/profile") || method.equals("PUT") && path.matches("/admin/users/[0-9]+")) {
            domains = List.of("authors", "articles", "search", "articleDetail", "comments");
        } else if (path.matches("/blog/articles/[0-9]+/comments") || path.matches("/(me|admin)/comments/[0-9]+(?:/review)?")) {
            domains = List.of("comments");
        } else if (path.equals("/admin/configs/site")) { domains = List.of("configs", "rss", "sitemap"); }
        else if (method.equals("DELETE") && path.matches("/admin/(visitors|page-views)/[0-9]+")) { domains = List.of("statistics"); }
        else {
            for (var pair : Map.of("/admin/home/profile", "home", "/admin/home/social-links", "social", "/admin/cv/experiences", "experiences", "/admin/cv/skills", "skills", "/admin/links", "links", "/admin/music", "music").entrySet()) {
                if (path.equals(pair.getKey()) || path.startsWith(pair.getKey() + "/")) { domains = List.of(pair.getValue()); break; }
            }
        }
        Map<String, String> before = new LinkedHashMap<>();
        domains.forEach(domain -> before.put(domain, redis.opsForValue().get(publicCacheStore.prefix(domain) + "generation")));
        return before;
    }

    private MockHttpServletRequestBuilder req(String method, String path) {
        return request(HttpMethod.valueOf(method), "/api/v1" + path).contextPath("/api/v1")
                .servletPath(path.split("\\?", 2)[0]);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private void code(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("code")
                .asString()).isEqualTo(code);
    }

    private void seedDictionary() {
        jdbc.update("INSERT INTO blog_category(category_id,category_name,slug) VALUES(700,'分类','category')");
        jdbc.update(
                "INSERT INTO blog_tag(tag_id,tag_name,slug) VALUES(701,'标签一','tag-one'),(702,'标签二','tag-two')");
    }

    private void comment(long commentId, String article, long user, Long parent, int status) {
        jdbc.update(
                "INSERT INTO blog_comment(comment_id,article_id,user_id,parent_id,content,status) VALUES(?,?,?,?,?,?)",
                commentId, article, user, parent, "评论", status);
    }

    private void addFile(long id, String uploader, String key) throws Exception {
        Files.write(files.getLocalRoot().resolve(key), new byte[]{1, 2, 3});
        jdbc.update(
                "INSERT INTO blog_file(file_id,uploader_id,original_name,storage_type,bucket,object_key,mime_type," +
                 "size_bytes) VALUES(?,?,?,'local','',?,'image/png',3)",
                id, uploader, key, key);
    }

    private String context(String article, String file) {
        return "/api/v1/blog/articles/" + article + "/files/" + file + "/content";
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试锁未释放");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /** 实际看到 InnoDB 锁等待才继续，不能睡一会儿就假定请求已经等到目标锁。 */
    private void awaitLockWait(String table) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            int count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l " +
                     "ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_NAME=?",
                    Integer.class, table);
            if (count > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("没有观察到目标InnoDB锁等待：" + table);
    }
}
