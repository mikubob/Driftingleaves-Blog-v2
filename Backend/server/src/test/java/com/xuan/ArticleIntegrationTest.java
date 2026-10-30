package com.xuan;

import com.xuan.audit.FailureAuditWriter;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** 第7..11章走真实 MVC、Service、Mapper 和隔离 MySQL/Redis，不替换核心业务，也不连接现有服务。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class ArticleIntegrationTest {
    private static IsolatedServices services;
    private static final String PASSWORD = "Example-password-2026";
    private static final String ORIGIN = "https://blog.example.test";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager transactions;
    @Autowired FileProperties files;
    @Autowired ArticleMapper articles;
    @Autowired SessionManager sessions;
    @MockitoSpyBean FailureAuditWriter failures;
    private final List<CompletableFuture<Void>> pending = new CopyOnWriteArrayList<>();

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
        registry.add("app.auth.redis-prefix", () -> "dl:article-test:");
        registry.add("app.auth.bcrypt-strength", () -> 4);
        registry.add("app.auth.allowed-origins", () -> ORIGIN);
        registry.add("app.auth.frontend-complete-url", () -> ORIGIN + "/oauth/complete");
        registry.add("app.auth.rate.register", () -> 200);
        registry.add("app.files.local-root", () -> services.directory.resolve("uploads").toString());
    }

    @AfterAll
    static void stop(@Autowired ConfigurableApplicationContext context) {
        context.getBean(LettuceConnectionFactory.class).destroy();
        context.getBean(HikariDataSource.class).close();
        if (services != null) {
            services.close();
        }
    }

    @BeforeEach
    void resetData() throws Exception {
        reset(failures);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<Void> result = (CompletableFuture<Void>) invocation.callRealMethod();
            pending.add(result);
            return result;
        }).when(failures).write(any(SysOperationLog.class));
        jdbc.execute("DROP TRIGGER IF EXISTS part7_fail");
        jdbc.update("DELETE FROM sys_operation_log");
        while (jdbc.update("DELETE c FROM blog_comment c LEFT JOIN blog_comment child ON child.parent_id=c.comment_id WHERE child.comment_id IS NULL") > 0) { }
        jdbc.update("DELETE FROM blog_article");
        jdbc.update("DELETE FROM blog_file");
        jdbc.update("DELETE FROM blog_category");
        jdbc.update("DELETE FROM blog_tag");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("DELETE FROM sys_role WHERE role_id > 3");
        jdbc.update("UPDATE sys_role SET status=1, data_scope=CASE WHEN role_id=1 THEN 'ALL' ELSE 'SELF' END");
        jdbc.update("UPDATE sys_permission SET status=1");
        jdbc.update("INSERT INTO blog_site_config(config_key,config_value,is_public) VALUES('comment',JSON_OBJECT('enabled',TRUE,'reviewRequired',TRUE),0) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value),is_public=0");
        jdbc.update("DELETE FROM blog_friend_link");
        jdbc.update("DELETE FROM blog_site_config WHERE config_key <> 'comment'");
        jdbc.update("UPDATE blog_site_config SET description=NULL WHERE config_key='comment'");
        jdbc.update("INSERT INTO blog_site_config(config_key,config_value,is_public) VALUES('site',JSON_OBJECT('title','DriftingLeaves','description','记录与分享','icp','','publicSecurityRecord','','startDate',NULL),1),('message',JSON_OBJECT('enabled',TRUE,'reviewRequired',TRUE),0)");
        // 每个测试先恢复初始化授权，避免上一条删改权限影响下一条。
        jdbc.update("INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT 1,permission_id FROM sys_permission");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN(2,3)");
        jdbc.update("INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT 2,permission_id FROM sys_permission WHERE permission_id IN(1,10,11,12,13,14,15,20,30,43,44,45,60,61,62,163,164,165,183,184)");
        jdbc.update("INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT 3,permission_id FROM sys_permission WHERE permission_id IN(43,44,45,163,164,165,183,184)");
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
    }

    @Test
    void createsDraftWithDefaultsCurrentAuthorSafeHtmlAndTransactionalAudit() throws Exception {
        String token = actor("author_defaults", 2);
        String markdown = "# 标题\n\n你好。 [word](https://example.test/target)\n\n<script>bad()</script><img src=x onerror=alert(1)>";
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
        assertThat(jdbc.queryForObject("SELECT target_id FROM sys_operation_log WHERE operation_target='blog:article' AND result=1", Long.class)).isEqualTo(Long.valueOf(id));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:article' AND error_message LIKE '%bad%'", Integer.class)).isZero();
        code(call("GET", "/blog/articles/First-Leaf", token, null), 404, "NOT_FOUND");
    }

    @Test
    void rejectsUnknownDuplicateWrongTypesIdsUnicodeLimitsAndLargeMarkdown() throws Exception {
        String token = actor("strict", 1);
        Map<String, Object> body = body("Strict", "source");
        for (String field : List.of("authorId", "status", "htmlContent", "publishTime", "deletedAt", "viewCount", "createTime", "present", "completeReplace", "uniqueTags")) {
            Map<String, Object> changed = new LinkedHashMap<>(body);
            changed.put(field, "1");
            code(call("POST", "/admin/articles", token, json.writeValueAsString(changed)), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/articles", token, "{\"title\":\"a\",\"title\":\"b\",\"slug\":\"s\",\"markdownContent\":\"\"}"), 400, "VALIDATION_ERROR");
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
                Map.entry("tagIds", List.of("null", "[1]", "[null]", "[\"01\"]", "[\"1\",\"1\"]", "[\"9223372036854775808\"]")));
        for (var entry : invalid.entrySet()) {
            for (String value : entry.getValue()) {
                Map<String, Object> changed = new LinkedHashMap<>(body);
                changed.remove(entry.getKey());
                String encoded = json.writeValueAsString(changed);
                code(call("POST", "/admin/articles", token, encoded.substring(0, encoded.length() - 1) + ",\"" + entry.getKey() + "\":" + value + "}"), 400, "VALIDATION_ERROR");
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
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON blog_article_tag FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated insert failure'");
        code(write("PUT", "/admin/articles/" + id, token, version, body), 503, "SERVICE_UNAVAILABLE");
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body)), 503, "SERVICE_UNAVAILABLE");
        assertThat(revision(token, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?", String.class, id)).isEqualTo("old");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:article' AND result=1", Integer.class)).isEqualTo(2);
    }

    @Test
    void revisionChecksPrecedeStateAndIdempotenceAndExcludeCountersAuditAndCache() throws Exception {
        String token = actor("revision", 1);
        String id = create(token, "Revision", "body");
        String version = revision(token, id);
        for (String path : List.of("/admin/articles/" + id, "/admin/articles/" + id + "/status", "/admin/articles/" + id + "/restore")) {
            String method = path.endsWith("restore") ? "POST" : "PUT";
            Object body = path.endsWith("status") ? Map.of("status", 1) : path.endsWith("restore") ? null : body("Revision", "body");
            code(write(method, path, token, null, body), 428, "PRECONDITION_REQUIRED");
            for (String invalid : List.of("*", "abc", "\"a\",\"b\"", "\"" + "A".repeat(64) + "\"")) {
                code(write(method, path, token, invalid, body), 400, "VALIDATION_ERROR");
            }
            code(write(method, path, token, "\"" + "0".repeat(64) + "\"", body), 412, "REVISION_CONFLICT");
        }
        code(write("DELETE", "/admin/articles/" + id, token, null, null), 428, "PRECONDITION_REQUIRED");
        jdbc.update("UPDATE blog_article SET view_count=100, update_time=DATE_ADD(update_time,INTERVAL 1 SECOND) WHERE article_id=?", id);
        jdbc.update("UPDATE blog_article_content SET html_content='<script>bad()</script>' WHERE article_id=?", id);
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", id, userId("revision"));
        assertThat(revision(token, id)).isEqualTo(version);
        code(write("PUT", "/admin/articles/" + id + "/status", token, version, Map.of("status", 1)), 200, "OK");
        String published = revision(token, id);
        code(write("PUT", "/admin/articles/" + id + "/status", token, version, Map.of("status", 1)), 412, "REVISION_CONFLICT");
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
        code(write("DELETE", "/admin/articles/" + id, token, revision(token, id), Map.of("authorId", "1")), 400, "VALIDATION_ERROR");
        code(write("POST", "/admin/articles/" + id + "/restore", token, revision(token, id), Map.of("status", 1)), 400, "VALIDATION_ERROR");
        code(status(token, id, 2), 409, "STATE_CONFLICT");
        code(write("POST", "/admin/articles/" + id + "/restore", token, revision(token, id), null), 409, "STATE_CONFLICT");
        MvcResult published = status(token, id, 1);
        code(published, 200, "OK");
        String first = data(published).get("publishTime").asString();
        code(status(token, id, 2), 200, "OK");
        code(status(token, id, 1), 200, "OK");
        assertThat(data(call("GET", "/admin/articles/" + id, token, null)).get("publishTime").asString()).isEqualTo(first);
        jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", id);
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", id, userId("states"));
        code(write("DELETE", "/admin/articles/" + id, token, revision(token, id), null), 200, "OK");
        String trash = revision(token, id);
        code(write("DELETE", "/admin/articles/" + id, token, trash, null), 200, "OK");
        code(write("PUT", "/admin/articles/" + id, token, trash, body("States", "new")), 409, "STATE_CONFLICT");
        code(write("PUT", "/admin/articles/" + id + "/status", token, trash, Map.of("status", 1)), 409, "STATE_CONFLICT");
        code(call("GET", "/blog/articles/States", token, null), 404, "NOT_FOUND");
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body("States", "duplicate"))), 409, "UNIQUE_CONFLICT");
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
            code(write("PUT", "/admin/articles/" + id + "/status", token, revision(token, id), Map.of("status", invalid)), 400, "VALIDATION_ERROR");
        }
        code(write("PUT", "/admin/articles/" + id + "/status", token, revision(token, id), Map.of("status", 1, "publishTime", "2020-01-01T00:00:00.000Z")), 400, "VALIDATION_ERROR");
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
            code(call("POST", "/admin/articles", token, json.writeValueAsString(body("Denied", "body"))), expected, error);
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
        assertThat(data(call("GET", "/admin/articles?authorId=" + userId("admin"), author, null)).get("total").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/admin/articles", author, null)).get("records").get(0).get("articleId").asString()).isEqualTo(own);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=2 AND permission_id=12");
        code(write("PUT", "/admin/articles/" + own, author, revision(author, own), body("Own", "new")), 403, "FORBIDDEN");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=1 AND permission_id=1");
        code(call("GET", "/admin/articles", admin, null), 403, "FORBIDDEN");
    }

    @Test
    void publishedUpdateUsesIndependentPublishScopeAndUnrelatedAllCannotExpandSelf() throws Exception {
        String admin = actor("scope_admin", 1);
        String author = actor("scope_author", 2);
        String id = create(admin, "Scoped", "body");
        code(status(admin, id, 1), 200, "OK");
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(800,'article_update_all','编辑全部','ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(800,12)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,800)", userId("scope_author"));
        code(write("PUT", "/admin/articles/" + id, author, null, body("Scoped", "new")), 404, "NOT_FOUND");
        code(write("PUT", "/admin/articles/" + id, author, revision(admin, id), body("Scoped", "new")), 404, "NOT_FOUND");
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
        String draft = create(token, "Draft", "body");
        String privateId = create(token, "Private", "body");
        String down = create(token, "Down", "body");
        String trash = create(token, "Trash", "body");
        String future = create(token, "Future", "body");
        jdbc.update("UPDATE blog_article SET status=1,visibility=2,publish_time=UTC_TIMESTAMP(3) WHERE article_id=?", privateId);
        jdbc.update("UPDATE blog_article SET status=2,publish_time=UTC_TIMESTAMP(3) WHERE article_id=?", down);
        jdbc.update("UPDATE blog_article SET status=1,deleted_at=UTC_TIMESTAMP(3),publish_time=UTC_TIMESTAMP(3) WHERE article_id=?", trash);
        jdbc.update("UPDATE blog_article SET status=1,publish_time=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY) WHERE article_id=?", future);
        for (String slug : List.of("Draft", "Private", "Down", "Trash", "Future", "public")) {
            code(call("GET", "/blog/articles/" + slug, token, null), 404, "NOT_FOUND");
        }
        JsonNode list = data(call("GET", "/blog/articles", null, null));
        assertThat(list.get("total").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles?categoryId=700", null, null)).get("total").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/articles?categoryId=999999", null, null)).get("total").asString()).isEqualTo("0");
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
        assertThat(data(call("GET", "/blog/archives", token, null)).get("records").get(0).get("articleCount").asString()).isEqualTo("2");
        code(call("GET", "/blog/articles/Public/files/1/content", null, null), 400, "VALIDATION_ERROR");
        code(call("GET", "/blog/unimplemented", null, null), 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, draft)).isZero();
    }

    @Test
    void searchIsLiteralAcrossMarkdownTitleSummaryWithAndFiltersAndStablePagination() throws Exception {
        String token = actor("search", 1);
        seedDictionary();
        String first = create(token, "Search-First", "literal %_\\ marker");
        String second = create(token, "Search-Second", "other");
        code(status(token, first, 1), 200, "OK");
        code(status(token, second, 1), 200, "OK");
        jdbc.update("UPDATE blog_article SET category_id=700,publish_time='2026-10-01 00:00:00.000', update_time='2026-10-01 00:00:00.000' WHERE article_id IN(?,?)", first, second);
        jdbc.update("INSERT INTO blog_article_tag(article_id,tag_id) VALUES(?,701)", first);
        jdbc.update("UPDATE blog_article SET summary='summary unique',is_top=1 WHERE article_id=?", second);
        for (String endpoint : List.of("/blog/articles", "/admin/articles")) {
            MvcResult found = mvc.perform(req("GET", endpoint).header("Authorization", "Bearer " + token).param("keyword", "%_\\")).andReturn();
            code(found, 200, "OK");
            assertThat(data(found).get("total").asString()).isEqualTo("1");
            assertThat(data(found).get("records").get(0).get("articleId").asString()).isEqualTo(first);
            assertThat(data(call("GET", endpoint + "?keyword=summary", token, null)).get("total").asString()).isEqualTo("1");
            assertThat(data(call("GET", endpoint + "?keyword=Search", token, null)).get("total").asString()).isEqualTo("2");
        }
        assertThat(data(call("GET", "/blog/articles?categoryId=700&tagId=701&month=2026-10", null, null)).get("total").asString()).isEqualTo("1");
        assertThat(data(call("GET", "/blog/articles?pageSize=1", null, null)).get("records").get(0).get("articleId").asString()).isEqualTo(second);
        JsonNode beyond = data(call("GET", "/blog/articles?page=4&pageSize=1", null, null));
        assertThat(beyond.get("page").asInt()).isEqualTo(4);
        assertThat(beyond.get("records").isEmpty()).isTrue();
        assertThat(beyond.get("totalPages").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles?month=9999-12", null, null)).get("total").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/articles?month=1000-01", null, null)).get("total").asString()).isEqualTo("0");
    }

    @Test
    void validatesQueryAndPathBoundariesWithoutSilentCorrection() throws Exception {
        String token = actor("queries", 1);
        for (String query : List.of("page=0", "page=100001", "pageSize=101", "pageSize=0", "page=1.2", "categoryId=01", "tagId=9223372036854775808")) {
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
        jdbc.update("UPDATE blog_article SET status=1,publish_time='2026-09-30 23:59:59.999' WHERE article_id=?", first);
        jdbc.update("UPDATE blog_article SET status=1,publish_time='2026-10-01 00:00:00.000' WHERE article_id=?", second);
        jdbc.update("UPDATE blog_article SET status=1,publish_time='2026-10-02 00:00:00.000' WHERE article_id=?", third);
        JsonNode archive = data(call("GET", "/blog/archives?pageSize=1", null, null));
        assertThat(archive.get("total").asString()).isEqualTo("2");
        assertThat(archive.get("totalPages").asString()).isEqualTo("2");
        assertThat(archive.get("records").get(0).get("month").asString()).isEqualTo("2026-10");
        assertThat(archive.get("records").get(0).get("articleCount").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles?month=2026-09", null, null)).get("total").asString()).isEqualTo("1");
        assertThat(data(call("GET", "/blog/articles?month=2026-10", null, null)).get("total").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/archives?page=3&pageSize=1", null, null)).get("records").isEmpty()).isTrue();
    }

    @Test
    void derivesReadingNavigationAndRelatedArticlesWithoutCountingCandidates() throws Exception {
        String token = actor("navigation", 1);
        seedDictionary();
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            String id = create(token, "Nav-" + index, "# 标题\n\n你好 abc123 [link](https://target.test/ignored)\n\n```java\nignored code words\n```");
            ids.add(id);
            jdbc.update("UPDATE blog_article SET status=1,category_id=700,publish_time='2026-10-01 00:00:00.000' WHERE article_id=?", id);
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
        assertThat(data(call("GET", "/blog/articles/Nav-4", null, null)).get("prevArticle").get("articleId").asString()).isEqualTo(ids.get(2));
        jdbc.update("UPDATE blog_category SET status=0 WHERE category_id=700");
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
        assertThat(data(call("GET", "/blog/articles/Comments", null, null)).get("commentCount").asString()).isEqualTo("3");
        JsonNode admin = data(call("GET", "/admin/articles/" + id, token, null));
        assertThat(admin.get("commentCount").asString()).isEqualTo("3");
        assertThat(admin.get("likeCount").asString()).isEqualTo("1");
        jdbc.update("UPDATE blog_comment SET status=2 WHERE comment_id=1001");
        assertThat(data(call("GET", "/blog/articles/Comments", null, null)).get("commentCount").asString()).isEqualTo("1");
    }

    @Test
    void fourRankingsUseCumulativeMetricsAndStablePublicCandidatesWithoutReads() throws Exception {
        String token = actor("rank", 1);
        String old = create(token, "Rank-Old", "body");
        String recent = create(token, "Rank-New", "body");
        String tie = create(token, "Rank-Tie", "body");
        String privateId = create(token, "Rank-Private", "body");
        LocalDateTime month = LocalDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay();
        jdbc.update("UPDATE blog_article SET status=1,publish_time=?,view_count=900 WHERE article_id=?", month.minusDays(1), old);
        jdbc.update("UPDATE blog_article SET status=1,publish_time=?,view_count=50 WHERE article_id IN(?,?)", month, recent, tie);
        jdbc.update("UPDATE blog_article SET status=1,publish_time=?,visibility=2,view_count=9999 WHERE article_id=?", month, privateId);
        jdbc.update("INSERT INTO blog_article_like(article_id,user_id) VALUES(?,?)", recent, userId("rank"));
        assertThat(data(call("GET", "/blog/article-rankings", null, null)).get(0).get("articleId").asString()).isEqualTo(old);
        JsonNode view = data(call("GET", "/blog/article-rankings?period=month&metric=view", null, null));
        assertThat(view.size()).isEqualTo(2);
        assertThat(view.get(0).get("articleId").asString()).isEqualTo(tie);
        assertThat(view.get(0).get("hotValue").asString()).isEqualTo("50");
        for (String period : List.of("site", "month")) {
            JsonNode likes = data(call("GET", "/blog/article-rankings?period=" + period + "&metric=like&limit=1", null, null));
            assertThat(likes.size()).isEqualTo(1);
            assertThat(likes.get(0).get("articleId").asString()).isEqualTo(recent);
            assertThat(likes.get(0).get("hotValue").asString()).isEqualTo("1");
        }
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, recent)).isEqualTo(50);
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
        assertThat(data(saved).get("htmlContent").asString()).contains("/api/v1/admin/articles/" + id + "/files/900/content");
        assertThat(data(saved).get("markdownContent").asString()).contains("/blog/");
        code(status(author, id, 1), 200, "OK");
        assertThat(data(call("GET", "/blog/articles/Files", null, null)).get("htmlContent").asString()).contains(context(id, "900"));
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
        for (String bad : List.of(context("99999", "900"), good + "?x=1", good + "#x", good.replace("/files/", "/%66iles/"),
                good.replace("/files/", "/%2566iles/"), good.replace("/files/", "/x/../files/"), good.replace("900/content", "0900/content"),
                "/api/v1/admin/articles/" + id + "/files/900/content", "/api/v1/admin/files/900/content", "/uploads/paths.png",
                ORIGIN + good.replace("/files/", "/%66iles/"), "//blog.example.test" + good)) {
            Map<String, Object> body = body("Paths", "[bad](" + bad + ")");
            code(write("PUT", "/admin/articles/" + id, token, revision(token, id), body), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/articles", token, json.writeValueAsString(body("Create-Path", "![x](" + good + ")"))), 400, "VALIDATION_ERROR");
        Map<String, Object> raw = body("Paths", "<img src=\"" + ORIGIN + good + "\">\n\n```\n" + good + "\n```\n\n[external](https://external.test" + good + ")");
        MvcResult result = write("PUT", "/admin/articles/" + id, token, revision(token, id), raw);
        code(result, 200, "OK");
        assertThat(data(result).get("htmlContent").asString()).contains("/api/v1/admin/articles/" + id + "/files/900/content", "https://external.test" + good, "<code>" + good);
        String version = revision(token, id);
        Files.delete(files.getLocalRoot().resolve("paths.png"));
        code(write("PUT", "/admin/articles/" + id, token, version, body("Paths", "only text " + good + "\n\n```\n" + good + "\n```")), 200, "OK");
    }

    @Test
    void concurrentAggregateWritesHaveOneWinnerAndReadsIncrementAtomically() throws Exception {
        String token = actor("concurrent", 1);
        String other = actor("concurrent_other", 1);
        String id = create(token, "Concurrent", "body");
        String version = revision(token, id);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return write("PUT", "/admin/articles/" + id, token, version, body("Concurrent", "first")); });
            var second = executor.submit(() -> { start.await(); return write("PUT", "/admin/articles/" + id, other, version, body("Concurrent", "second")); });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS).getResponse().getStatus(), second.get(10, TimeUnit.SECONDS).getResponse().getStatus())).containsExactlyInAnyOrder(200, 412);
            code(status(token, id, 1), 200, "OK");
            List<Future<MvcResult>> reads = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                reads.add(executor.submit(() -> call("GET", "/blog/articles/Concurrent", null, null)));
            }
            for (var read : reads) {
                code(read.get(15, TimeUnit.SECONDS), 200, "OK");
            }
        }
        assertThat(jdbc.queryForObject("SELECT view_count FROM blog_article WHERE article_id=?", Long.class, id)).isEqualTo(12);
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
                jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class, id);
                locked.countDown();
                await(release);
                jdbc.update("UPDATE blog_article_content SET markdown_content='changed while waiting' WHERE article_id=?", id);
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var waiting = executor.submit(() -> write("PUT", "/admin/articles/" + id, token, version, body("Waiting", "overwrite")));
            awaitLockWait("blog_article");
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            code(waiting.get(10, TimeUnit.SECONDS), 412, "REVISION_CONFLICT");
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?", String.class, id)).isEqualTo("changed while waiting");
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
            var writer = executor.submit(() -> write("PUT", "/admin/articles/" + id, token, version, body("File-Lock", "![x](" + context(id, "900") + ")")));
            awaitLockWait("blog_file");
            // 写请求等文件锁时，另一事务还能拿文章锁，说明它没有先拿文章锁再等文件锁。
            new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET innodb_lock_wait_timeout=2");
                jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class, id);
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
            code(write("DELETE", "/admin/permissions/" + permission, token, detail.getResponse().getHeader("X-Revision"), null), 409, "STATE_CONFLICT");
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
                    jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class, id);
                    locked.countDown();
                    await(release);
                    if ("permission".equals(mode)) {
                        // 这里直接改隔离测试数据来检查锁后重查，生产授权写入仍要遵守用户锁顺序。
                        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=12");
                    }
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var writer = executor.submit(() -> write("PUT", "/admin/articles/" + id, token, version, body("Wait-" + mode, "overwrite")));
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
            assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?", String.class, id)).isEqualTo("body");
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
                jdbc.queryForObject("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", Long.class, id);
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
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        Map<String, Object> body = body("Audit", "changed");
        body.put("tagIds", List.of("701"));
        code(write("PUT", "/admin/articles/" + id, token, version, body), 503, "SERVICE_UNAVAILABLE");
        assertThat(revision(token, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_tag WHERE article_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT markdown_content FROM blog_article_content WHERE article_id=?", String.class, id)).isEqualTo("body");
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
        code(write("PUT", "/admin/articles/" + id + "/status", token, version, Map.of("status", 1)), 412, "REVISION_CONFLICT");
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
        MvcResult created = call("POST", "/admin/categories", token, "{\"categoryName\":\" 技术 🌿 \",\"slug\":\" Tech \"}");
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
        assertThat(jdbc.queryForObject("SELECT target_id FROM sys_operation_log WHERE operation_target='blog:category' AND operation_type='create' AND result=1", String.class)).isEqualTo(id);
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
        assertThat(jdbc.queryForObject("SELECT target_id FROM sys_operation_log WHERE operation_target='blog:tag' AND operation_type='create' AND result=1", String.class)).isEqualTo(id);
    }

    @Test
    void dictionaryPagesUseLiteralNamesStableOrderStatusAndMatchingTotals() throws Exception {
        String token = actor("dictionary_pages", 1);
        jdbc.update("INSERT INTO blog_category(category_id,category_name,slug,sort_order,status) VALUES(710,'Z%_','z',-1,1),(711,'A%_','a',-1,1),(712,'隐藏','hidden',-2,0),(713,'空分类','empty',0,1)");
        jdbc.update("INSERT INTO blog_tag(tag_id,tag_name,slug) VALUES(710,'Z%_','z'),(711,'A%_','a'),(712,'空标签','empty')");
        MvcResult categories = call("GET", "/blog/categories?pageSize=1", null, null);
        code(categories, 200, "OK");
        assertThat(categories.getResponse().getHeader("X-Revision")).isNull();
        assertThat(data(categories).get("total").asString()).isEqualTo("3");
        assertThat(data(categories).get("totalPages").asString()).isEqualTo("3");
        assertThat(data(categories).get("records").get(0).get("categoryId").asString()).isEqualTo("710");
        assertThat(data(call("GET", "/blog/categories?page=2&pageSize=1", null, null)).get("records").get(0).get("categoryId").asString()).isEqualTo("711");
        MvcResult filtered = mvc.perform(req("GET", "/admin/categories").header("Authorization", "Bearer " + token).param("keyword", " %_ ")).andReturn();
        code(filtered, 200, "OK");
        assertThat(data(filtered).get("total").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/admin/categories?status=0", token, null)).get("total").asString()).isEqualTo("1");
        MvcResult tags = mvc.perform(req("GET", "/admin/tags").header("Authorization", "Bearer " + token).param("keyword", "%_").param("pageSize", "1")).andReturn();
        code(tags, 200, "OK");
        assertThat(data(tags).get("total").asString()).isEqualTo("2");
        assertThat(data(tags).get("records").get(0).get("tagId").asString()).isEqualTo("710");
        assertThat(data(call("GET", "/blog/tags", null, null)).get("total").asString()).isEqualTo("3");
        JsonNode empty = data(call("GET", "/admin/tags?page=100", token, null));
        assertThat(empty.get("page").asInt()).isEqualTo(100);
        assertThat(empty.get("records").isEmpty()).isTrue();
        jdbc.update("DELETE FROM blog_tag");
        JsonNode zero = data(call("GET", "/blog/tags", null, null));
        assertThat(zero.get("total").asString()).isEqualTo("0");
        assertThat(zero.get("totalPages").asString()).isEqualTo("0");
        for (String path : List.of("/blog/categories?page=0", "/blog/tags?pageSize=101", "/admin/categories?status=2", "/admin/categories?page=100001")) {
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
            for (String extra : List.of("statusUnknown", "createTime", "updateTime", "authorId", "completeReplace", "descriptionPresent", category ? "categoryId" : "tagId")) {
                Map<String, Object> body = new LinkedHashMap<>(valid);
                body.put(extra, "1");
                code(call("POST", path, token, json.writeValueAsString(body)), 400, "VALIDATION_ERROR");
            }
            String name = category ? "categoryName" : "tagName";
            for (String bad : List.of("null", "12", "true", "\"  \"", json.writeValueAsString("🌿".repeat(65)))) {
                code(call("POST", path, token, "{\"" + name + "\":" + bad + ",\"slug\":\"valid\"}"), 400, "VALIDATION_ERROR");
            }
            for (String slug : List.of("null", "1", "\"a--b\"", "\"中文\"", json.writeValueAsString("a".repeat(101)))) {
                code(call("POST", path, token, "{\"" + name + "\":\"新名称\",\"slug\":" + slug + "}"), 400, "VALIDATION_ERROR");
            }
            code(call("POST", path, token, "{\"" + name + "\":\"a\",\"" + name + "\":\"b\",\"slug\":\"dup\"}"), 400, "VALIDATION_ERROR");
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
            code(call("POST", "/admin/categories", token, "{\"categoryName\":\"new\",\"slug\":\"new\",\"sortOrder\":" + value + "}"), 400, "VALIDATION_ERROR");
        }
        for (String value : List.of("null", "\"1\"", "2", "true")) {
            code(call("POST", "/admin/categories", token, "{\"categoryName\":\"new\",\"slug\":\"new\",\"status\":" + value + "}"), 400, "VALIDATION_ERROR");
        }
        code(call("POST", "/admin/categories", token, json.writeValueAsString(categoryBody("new", "new", "🌿".repeat(256), 0, 1))), 400, "VALIDATION_ERROR");
    }

    @Test
    void dictionaryUniquenessUsesDatabaseCollationAndCaseSensitiveSlugs() throws Exception {
        String token = actor("dictionary_unique", 1);
        for (String kind : List.of("categories", "tags")) {
            String path = "/admin/" + kind;
            String name = kind.equals("categories") ? "categoryName" : "tagName";
            MvcResult first = call("POST", path, token, json.writeValueAsString(Map.of(name, "Café", "slug", "Case")));
            code(first, 201, "OK");
            code(call("POST", path, token, json.writeValueAsString(Map.of(name, "cafe", "slug", "another"))), 409, "UNIQUE_CONFLICT");
            code(call("POST", path, token, json.writeValueAsString(Map.of(name, "另一个", "slug", "Case"))), 409, "UNIQUE_CONFLICT");
            MvcResult second = call("POST", path, token, json.writeValueAsString(Map.of(name, "另一个", "slug", "case")));
            code(second, 201, "OK");
            String id = data(second).get(kind.equals("categories") ? "categoryId" : "tagId").asString();
            String target = path + "/" + id;
            String version = dictionaryRevision(token, target);
            Object duplicate = kind.equals("categories") ? categoryBody("cafe", "case", null, 0, 1) : Map.of(name, "cafe", "slug", "case");
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
            Object body = path.endsWith("categories") ? categoryBody("新分类", "new", null, 0, 1) : Map.of("tagName", "新标签", "slug", "new");
            String target = path + (path.endsWith("categories") ? "/700" : "/701");
            code(call("GET", target, author, null), 200, "OK");
            code(write("POST", path, author, null, body), 403, "FORBIDDEN");
            code(write("PUT", target, author, null, body), 403, "FORBIDDEN");
            code(write("DELETE", target, author, null, null), 403, "FORBIDDEN");
        }
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(4,'taxonomy_self','字典维护','SELF')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT 4,permission_id FROM sys_permission WHERE permission_id IN(1,20,21,22,23,30,31,32,33)");
        String self = actor("taxonomy_self", 4);
        code(write("PUT", "/admin/categories/700", self, dictionaryRevision(self, "/admin/categories/700"), categoryBody("SELF分类", "self-category", null, 0, 1)), 200, "OK");
        code(write("PUT", "/admin/tags/701", self, dictionaryRevision(self, "/admin/tags/701"), Map.of("tagName", "SELF标签", "slug", "self-tag")), 200, "OK");
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
        MvcResult category = call("POST", "/admin/categories", token, "{\"categoryName\":\"公开分类\",\"slug\":\"public-category\"}");
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
        code(write("PUT", categoryPath, token, dictionaryRevision(token, categoryPath), categoryBody("改名分类", "renamed-category", null, 0, 0)), 200, "OK");
        String tagPath = "/admin/tags/" + tagId;
        code(write("PUT", tagPath, token, dictionaryRevision(token, tagPath), Map.of("tagName", "新标签", "slug", "new-tag")), 200, "OK");
        JsonNode publicArticle = data(call("GET", "/blog/articles/Linked", null, null));
        assertThat(publicArticle.get("category").isNull()).isTrue();
        assertThat(publicArticle.get("tags").get(0).get("tagName").asString()).isEqualTo("新标签");
        assertThat(data(call("GET", "/blog/articles?categoryId=" + categoryId, null, null)).get("total").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/blog/articles?tagId=" + tagId, null, null)).get("total").asString()).isEqualTo("1");
        JsonNode adminArticle = data(call("GET", "/admin/articles/" + id, token, null));
        assertThat(adminArticle.get("category").get("categoryName").asString()).isEqualTo("改名分类");
        assertThat(adminArticle.get("category").get("status").asInt()).isZero();
        assertThat(revision(token, id)).isEqualTo(articleVersion);
        code(write("PUT", "/admin/articles/" + id, token, articleVersion, body), 200, "OK");
        code(write("PUT", categoryPath, token, dictionaryRevision(token, categoryPath), categoryBody("改名分类", "renamed-category", null, 0, 1)), 200, "OK");
        assertThat(data(call("GET", "/blog/articles?categoryId=" + categoryId, null, null)).get("total").asString()).isEqualTo("1");
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
        assertThat(data(call("GET", "/blog/articles?tagId=" + tagId, null, null)).get("total").asString()).isEqualTo("0");
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
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        for (String kind : List.of("categories", "tags")) {
            String path = "/admin/" + kind;
            String target = path + (kind.equals("categories") ? "/700" : "/701");
            String version = dictionaryRevision(token, target);
            Object next = kind.equals("categories") ? categoryBody("新分类", "new-category", "说明", 1, 0) : Map.of("tagName", "新标签", "slug", "new-tag");
            code(write("POST", path, token, null, next), 503, "SERVICE_UNAVAILABLE");
            code(write("PUT", target, token, version, next), 503, "SERVICE_UNAVAILABLE");
            assertThat(dictionaryRevision(token, target)).isEqualTo(version);
            code(write("DELETE", target, token, version, null), 503, "SERVICE_UNAVAILABLE");
            assertThat(dictionaryRevision(token, target)).isEqualTo(version);
            assertThat(revision(token, id)).isEqualTo(original);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_category", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_tag", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_tag WHERE article_id=?", Integer.class, id)).isEqualTo(1);
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
                Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                    jdbc.queryForList("SELECT * FROM blog_" + kind + " WHERE " + kind + "_id=? FOR UPDATE", category ? 700 : 701);
                    locked.countDown();
                    await(release);
                    jdbc.update("UPDATE blog_" + kind + " SET " + kind + "_name='锁内已修改' WHERE " + kind + "_id=?", category ? 700 : 701);
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
                Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                    jdbc.queryForList("SELECT * FROM blog_" + kind + " WHERE " + kind + "_id=? FOR SHARE", category ? 700 : 701);
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
                Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                    jdbc.queryForList("SELECT * FROM blog_" + kind + " WHERE " + kind + "_id=? FOR UPDATE", category ? 700 : 701);
                    locked.countDown();
                    await(release);
                }));
                await(locked);
                Future<MvcResult> deletion = pool.submit(() -> write("DELETE", path, token, version, null));
                try {
                    awaitLockWait("blog_" + kind);
                    // 这里只改隔离测试数据，生产撤权仍先锁操作者，不能绕过用户锁。
                    jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code=?", "blog:" + kind + ":delete");
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
        assertThat(publicComment.get("author").propertyNames()).containsExactlyInAnyOrder("userId", "nickname", "avatar");
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
            jdbc.update("UPDATE blog_article SET status=1,visibility=1,deleted_at=NULL,publish_time=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 DAY) WHERE article_id=?", article);
            jdbc.update("UPDATE blog_article SET " + state + " WHERE article_id=?", article);
            code(call("GET", commentsPath(article), null, null), 404, "NOT_FOUND");
            code(postComment(token, article, "不能写", null), 404, "NOT_FOUND");
            for (String method : List.of("GET", "PUT", "DELETE")) {
                code(call(method, likePath(article), token, null), 404, "NOT_FOUND");
            }
            assertThat(data(call("GET", "/me/comments?articleId=" + article, token, null)).get("total").asString()).isEqualTo("1");
            code(call("GET", "/admin/comments/" + id, token, null), 200, "OK");
        }
        code(review(token, id, 1), 200, "OK");
        code(call("DELETE", "/me/comments/" + id, token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like", Integer.class)).isEqualTo(1);
        assertThat(data(call("GET", "/me/comments", token, null)).get("records").get(0).has("markdownContent")).isFalse();
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
            code(call("POST", commentsPath(article), token, json.writeValueAsString(Map.of("content", "a", key, "1"))), 400, "VALIDATION_ERROR");
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
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(900,'comment_all','管理删除ALL','ALL')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(900,42)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,900)", userId("comment_scope_author"));
        JsonNode list = data(call("GET", "/admin/comments", author, null));
        assertThat(list.get("total").asString()).isEqualTo("1");
        assertThat(list.get("records").get(0).get("commentId").asString()).isEqualTo(own);
        assertThat(data(call("GET", "/admin/comments?userId=" + userId("comment_scope_author"), author, null)).get("total").asString()).isEqualTo("0");
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
        assertThat(data(call("GET", "/me/comments?articleId=" + article + "&status=0", admin, null)).get("total").asString()).isEqualTo("1");
        for (int i = 0; i < 2; i++) {
            MvcResult deleted = call("DELETE", "/me/comments/" + own, admin, null);
            code(deleted, 200, "OK");
            assertThat(deleted.getResponse().getHeader("X-Revision")).isNull();
        }
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, own)).isEqualTo("该评论已删除");
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, theirs)).isEqualTo("别人的原文");
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
        JsonNode root = data(call("GET", commentsPath(article) + "?page=2&pageSize=1", null, null)).get("records").get(0);
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
        assertThat(data(call("GET", "/blog/articles/Comment-counts", null, null)).get("commentCount").asString()).isEqualTo("2");
        assertThat(data(call("GET", "/blog/articles", null, null)).get("records").get(0).get("commentCount").asString()).isEqualTo("2");
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:comment' AND result=1", Integer.class)).isEqualTo(6);
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
        assertThat(data(call("GET", "/blog/articles/Like-first", null, null)).get("likeCount").asString()).isEqualTo("2");
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=? AND user_id=?", Integer.class, first, userId("like_member"))).isEqualTo(1);
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
        }).header("Authorization", "Bearer " + token).contentType("application/json").content("{\"content\":\"换IP仍超用户限制\"}")).andReturn();
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
        assertThat(AuthorizationProvider.ADMIN_PERMISSIONS).doesNotContainAnyElementsOf(AuthorizationProvider.INTERACTION_PERMISSIONS);
    }

    @Test
    void commentAuditFailureRollsBackReviewAndPlaceholderAndFailureIsRecordedAfterRollback() throws Exception {
        String token = actor("comment_audit", 1);
        String article = published(token, "Comment-audit");
        String id = commentId(postComment(token, article, "不能丢失的原文", null));
        String version = commentRevision(token, id);
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        code(review(token, id, 1), 503, "SERVICE_UNAVAILABLE");
        code(call("DELETE", "/admin/comments/" + id, token, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(commentRevision(token, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, id)).isEqualTo("不能丢失的原文");
        finishAudits();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:comment' AND result=0", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:comment' AND result=1", Integer.class)).isZero();
    }

    @Test
    void failedCommentAndLikeInsertsLeaveNoPartialRowsOrCounts() throws Exception {
        String token = actor("interaction_insert_fail", 1);
        String article = published(token, "Insert-fail");
        for (String table : List.of("blog_comment", "blog_article_like")) {
            jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON " + table + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated insert failure'");
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
                    jdbc.queryForList("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE NOWAIT", article);
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
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE article_id=?", Integer.class, article)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=?", Integer.class, article))
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
                    case "like", "unlike" -> call(action.equals("like") ? "PUT" : "DELETE", likePath(article), token, null);
                    case "own" -> call("DELETE", "/me/comments/" + id, token, null);
                    case "review" -> write("PUT", "/admin/comments/" + id + "/review", token, version, Map.of("status", 1));
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
                        mode.equals("token") ? "UNAUTHENTICATED" : mode.equals("disabled") ? "ACCOUNT_DISABLED" : "FORBIDDEN");
                assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, id)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment WHERE article_id=?", Integer.class, article)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like WHERE article_id=?", Integer.class, article))
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
            jdbc.update("UPDATE blog_article SET author_id=? WHERE article_id=?", userId("comment_scope_wait_author"), article);
            String version = commentRevision(author, id);
            MvcResult result = waitingInteraction("blog_article",
                    "SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", article,
                    () -> action.equals("review")
                            ? write("PUT", "/admin/comments/" + id + "/review", author, version, Map.of("status", 1))
                            : call("DELETE", "/admin/comments/" + id, author, null),
                    () -> jdbc.update("UPDATE blog_article SET author_id=? WHERE article_id=?", userId("comment_scope_wait_admin"), article));
            code(result, 404, "NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, id)).isZero();
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
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, id)).isEqualTo("持锁时替换的原文");
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
                work.add(pool.submit(() -> write("PUT", "/admin/comments/" + id + "/review", admin, version, Map.of("status", status))));
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
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, id)).isEqualTo(3);
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
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, id)).isEqualTo(3);
        }
        String id = commentId(postComment(token, article, "待审直接驳回", null));
        code(review(token, id, 2), 200, "OK");
        code(status(token, article, 2), 200, "OK");
        code(call("DELETE", "/admin/comments/" + id, token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT content FROM blog_comment WHERE comment_id=?", String.class, id)).isEqualTo("该评论已删除");
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
                Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(state -> {
                    if (action.equals("like")) {
                        jdbc.queryForList("SELECT article_id FROM blog_article WHERE article_id=? FOR UPDATE", article);
                    } else {
                        jdbc.queryForList("SELECT config_key FROM blog_site_config WHERE config_key='comment' FOR UPDATE");
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
                            case "review" -> write("PUT", "/admin/comments/" + id + "/review", token, version, Map.of("status", 1));
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
        assertThat(jdbc.queryForList("SELECT target_id FROM sys_operation_log WHERE operation_target='blog:link' AND result=1", String.class))
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
        assertThat(page.get("records").get(0).propertyNames()).containsExactlyInAnyOrder("linkId", "linkName", "url", "avatar", "description", "sortOrder");
        assertThat(data(mvc.perform(req("GET", "/admin/links").header("Authorization", "Bearer " + token)
                .param("keyword", "%_").param("status", "1")).andReturn()).get("total").asString()).isEqualTo("1");
        assertThat(data(call("GET", "/admin/links?status=2", token, null)).get("total").asString()).isEqualTo("1");
        String path = "/admin/links/" + hidden;
        code(write("PUT", path, token, dictionaryRevision(token, path), linkBody("now public", -1, 1)), 200, "OK");
        assertThat(data(call("GET", "/blog/links", null, null)).get("records").get(0).get("linkId").asString()).isEqualTo(hidden);
        path = "/admin/links/" + first;
        code(write("DELETE", path, token, dictionaryRevision(token, path), null), 200, "OK");
        assertThat(data(call("GET", "/blog/links", null, null)).get("total").asString()).isEqualTo("2");
        JsonNode empty = data(call("GET", "/blog/links?page=100000&pageSize=100", null, null));
        assertThat(empty.get("page").asInt()).isEqualTo(100000);
        assertThat(empty.get("records").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_friend_link WHERE link_id=?", Integer.class, first)).isZero();
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
        code(call("POST", "/admin/links", token, json.writeValueAsString(linkBody("🌿".repeat(101), 0, 0))), 400, "VALIDATION_ERROR");
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
        jdbc.update("INSERT INTO blog_site_config(config_key,config_value,is_public) VALUES('secret',JSON_OBJECT('password','never-output'),1)");
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
        assertThat(data(call("GET", "/blog/configs", null, null)).get(0).get("configValue").get("title").asString()).isEqualTo("🌿站点");
        code(updateConfig(token, "site", configBody(value, 0)), 200, "OK");
        assertThat(data(call("GET", "/blog/configs", null, null)).isEmpty()).isTrue();
        assertThat(data(call("GET", "/admin/configs/site", token, null)).get("description").isNull()).isTrue();
    }

    @Test
    void configInnerSchemaRejectsWrongFieldsTypesDatesAndVisibility() throws Exception {
        String token = actor("config_schema", 1);
        for (String key : List.of("comment", "message")) {
            for (Object value : List.of(Map.of(), Map.of("enabled", true), Map.of("enabled", 1, "reviewRequired", true),
                    Map.of("enabled", "true", "reviewRequired", false), Map.of("enabled", true, "reviewRequired", false, "secret", "x"))) {
                code(updateConfig(token, key, configBody(value, 0)), 400, "VALIDATION_ERROR");
            }
            code(updateConfig(token, key, configBody(Map.of("enabled", true, "reviewRequired", true), 1)), 400, "VALIDATION_ERROR");
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
                    .header("If-Revision", version).contentType("application/json").content(body)).andReturn(), 400, "VALIDATION_ERROR");
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
        jdbc.update("UPDATE blog_site_config SET config_value=JSON_OBJECT('startDate',NULL,'publicSecurityRecord','','icp','','description','记录与分享','title','DriftingLeaves'),update_time='2020-01-01' WHERE config_key='site'");
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
        code(updateConfig(token, "comment", configBody(Map.of("enabled", true, "reviewRequired", false), 0)), 200, "OK");
        String id = commentId(postComment(token, article, "免审", null));
        assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, id)).isEqualTo(1);
        code(updateConfig(token, "comment", configBody(Map.of("enabled", false, "reviewRequired", true), 0)), 200, "OK");
        code(postComment(token, article, "关闭拒绝", null), 409, "STATE_CONFLICT");
        assertThat(data(call("GET", commentsPath(article), null, null)).get("total").asString()).isEqualTo("1");
        code(call("PUT", likePath(article), token, null), 200, "OK");
        code(call("DELETE", "/me/comments/" + id, token, null), 200, "OK");
        code(updateConfig(token, "comment", configBody(Map.of("enabled", true, "reviewRequired", true), 0)), 200, "OK");
        String pending = commentId(postComment(token, article, "管理员也待审", null));
        assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE comment_id=?", Integer.class, pending)).isZero();
        code(updateConfig(token, "message", configBody(Map.of("enabled", false, "reviewRequired", false), 0)), 200, "OK");
        assertThat(data(call("GET", "/admin/configs/message", token, null)).get("configValue").get("enabled").asBoolean()).isFalse();
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
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT 3,permission_id FROM sys_permission WHERE permission_id IN(50,51,52,53,70,71)");
        code(call("GET", "/admin/configs", member, null), 403, "FORBIDDEN");
        code(call("POST", "/admin/links", member, json.writeValueAsString(linkBody("no admin", 0, 0))), 403, "FORBIDDEN");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN(1,3) AND permission_id IN(51,52,53,71)");
        code(call("GET", path, admin, null), 200, "OK");
        code(call("GET", "/admin/configs/site", admin, null), 200, "OK");
        code(call("POST", "/admin/links", admin, json.writeValueAsString(linkBody("no create", 0, 0))), 403, "FORBIDDEN");
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
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT 2,permission_id FROM sys_permission WHERE permission_id IN(51,52,53,71)");
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
            for (String query : List.of("page=0", "page=100001", "pageSize=101", "pageSize=0", "page=x", "page=", "page=1&page=2", "authorId=1")) {
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
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated audit failure'; END IF; END");
        code(call("POST", "/admin/links", token, json.writeValueAsString(linkBody("cannot save", 0, 0))), 503, "SERVICE_UNAVAILABLE");
        code(write("PUT", path, token, version, linkBody("cannot replace", 0, 1)), 503, "SERVICE_UNAVAILABLE");
        code(write("DELETE", path, token, version, null), 503, "SERVICE_UNAVAILABLE");
        code(write("PUT", "/admin/configs/site", token, configVersion, configBody(siteValue(), 0)), 503, "SERVICE_UNAVAILABLE");
        assertThat(dictionaryRevision(token, path)).isEqualTo(version);
        assertThat(dictionaryRevision(token, "/admin/configs/site")).isEqualTo(configVersion);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_friend_link", Integer.class)).isEqualTo(1);
        finishAudits();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE result=0", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE result=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE error_message LIKE '%cannot%'", Integer.class)).isZero();
    }

    @Test
    void siteWritesWaitingForTheirRowsCheckNewlyCommittedVersions() throws Exception {
        String token = actor("site_version_wait", 1);
        String id = newLink(token, linkBody("before wait", 0, 0));
        String path = "/admin/links/" + id;
        for (String method : List.of("PUT", "DELETE")) {
            String version = dictionaryRevision(token, path);
            MvcResult result = waitingInteraction("blog_friend_link", "SELECT link_id FROM blog_friend_link WHERE link_id=? FOR UPDATE", id,
                    () -> write(method, path, token, version, method.equals("PUT") ? linkBody("do not save", 0, 0) : null),
                    () -> jdbc.update("UPDATE blog_friend_link SET sort_order=sort_order+1 WHERE link_id=?", id), true);
            code(result, 412, "REVISION_CONFLICT");
        }
        String configVersion = dictionaryRevision(token, "/admin/configs/site");
        MvcResult result = waitingInteraction("blog_site_config", "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "site",
                () -> write("PUT", "/admin/configs/site", token, configVersion, configBody(siteValue(), 1)),
                () -> jdbc.update("UPDATE blog_site_config SET description='committed while waiting' WHERE config_key='site'"), true);
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
                String table = change.equals("account") ? "sys_user" : kind.equals("link") ? "blog_friend_link" : "blog_site_config";
                String sql = change.equals("account") ? "SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE"
                        : kind.equals("link") ? "SELECT link_id FROM blog_friend_link WHERE link_id=? FOR UPDATE"
                        : "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE";
                Object target = change.equals("account") ? userId(name) : id;
                MvcResult result = waitingInteraction(table, sql, target,
                        () -> write("PUT", path, token, version, kind.equals("link") ? linkBody("forbidden", 0, 1) : configBody(siteValue(), 1)),
                        () -> {
                            switch (change) {
                                case "token" -> sessions.revokeAll(Long.parseLong(userId(name)));
                                case "permission" -> jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code=?", permission);
                                default -> jdbc.update("UPDATE sys_user SET status=0 WHERE username=?", name);
                            }
                        }, !change.equals("account"));
                code(result, change.equals("token") ? 401 : 403,
                        change.equals("token") ? "UNAUTHENTICATED" : change.equals("permission") ? "FORBIDDEN" : "ACCOUNT_DISABLED");
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
            MvcResult result = waitingInteraction("blog_site_config", "SELECT config_key FROM blog_site_config WHERE config_key=? FOR UPDATE", "comment",
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
            String path = kind.equals("link") ? "/admin/links/" + newLink(first, linkBody("initial", 0, 0)) : "/admin/configs/site";
            String version = dictionaryRevision(first, path);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<MvcResult> a = pool.submit(() -> write("PUT", path, first, version,
                        kind.equals("link") ? linkBody("first", 0, 1) : configBody(siteValue(), 0)));
                Future<MvcResult> b = pool.submit(() -> write("PUT", path, second, version,
                        kind.equals("link") ? linkBody("second", 0, 2) : configBody(siteValue(), 1)));
                assertThat(List.of(a.get(10, TimeUnit.SECONDS).getResponse().getStatus(), b.get(10, TimeUnit.SECONDS).getResponse().getStatus()))
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

    private String commentsPath(String article) { return "/blog/articles/" + article + "/comments"; }
    private String likePath(String article) { return "/blog/articles/" + article + "/like"; }

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
        return write("PUT", "/admin/comments/" + id + "/review", token, commentRevision(token, id), Map.of("status", status));
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
            assertThat(content.getResponse().getContentType()).isEqualTo(extension.equals("png") ? "image/png" : "image/jpeg");
            assertThat(content.getResponse().getHeader("Content-Length")).isEqualTo(Integer.toString(image.length));
            assertThat(content.getResponse().getHeader("Content-Disposition")).contains("inline", "filename*=UTF-8''");
            assertThat(content.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(content.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(jdbc.queryForObject("SELECT object_key FROM blog_file WHERE file_id=?", String.class, id)).matches("[A-Za-z0-9_-]+\\.(png|jpg|jpeg)");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:file' AND operation_type='upload' AND target_id=? AND result=1", Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    void uploadRejectsSpoofedTypesTruncationAndUnsupportedImages() throws Exception {
        String token = actor("upload_types", 2);
        byte[] png = image("png");
        code(upload(token, "fake.jpg", "image/jpeg", png), 400, "VALIDATION_ERROR");
        code(upload(token, "fake.png", "image/png", image("jpg")), 400, "VALIDATION_ERROR");
        code(upload(token, "fake.png", "image/png", "<script>bad</script>".getBytes(StandardCharsets.UTF_8)), 400, "VALIDATION_ERROR");
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
        var builder = multipart("/api/v1/admin/files").file(new MockMultipartFile("file", "leaf.png", "image/png", image("png")));
        builder.contextPath("/api/v1").servletPath("/admin/files").header("Authorization", "Bearer " + token);
        builder.with(request -> { request.setContent(new byte[11 * 1024 * 1024 + 1]); return request; });
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
            if (variant.equals("duplicate")) { builder.file(new MockMultipartFile("file", "leaf2.png", "image/png", bytes)); }
            if (variant.equals("other")) { builder.file(new MockMultipartFile("other", "leaf.png", "image/png", bytes)); }
            if (variant.equals("uploader")) { builder.param("uploaderId", userId("upload_fields")); }
            if (variant.equals("query")) { builder.queryParam("uploaderId", userId("upload_fields")); }
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
        assertThat(data(call("GET", "/admin/files?uploaderId=" + userId("file_page_admin"), author, null)).get("total").asString()).isEqualTo("0");
        assertThat(data(call("GET", "/admin/files?mimeType=image/jpeg", admin, null)).get("total").asString()).isEqualTo("0");
        JsonNode empty = data(call("GET", "/admin/files?page=100&pageSize=1", admin, null));
        assertThat(empty.get("page").asInt()).isEqualTo(100);
        assertThat(empty.get("totalPages").asString()).isEqualTo("3");
        assertThat(empty.get("records").isEmpty()).isTrue();
        code(call("GET", "/admin/files/" + first, author, null), 200, "OK");
        for (String query : List.of("page=0", "pageSize=101", "page=1&page=2", "status=1", "uploaderId=01", "uploaderId=9223372036854775808", "keyword=", "mimeType=")) {
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
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(800,'file_query_all','查全部','ALL')");
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
        assertThat(call("GET", "/admin/files/" + id + "/content", token, null).getResponse().getStatus()).isEqualTo(200);
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
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), body("File-Public", "![x](" + context(article, id) + ")")), 200, "OK");
        code(call("GET", path, token, null), 404, "NOT_FOUND");
        code(status(token, article, 1), 200, "OK");
        assertThat(call("GET", path, null, null).getResponse().getContentAsByteArray()).containsExactly(image("png"));
        for (String update : List.of("visibility=2", "status=2", "deleted_at=UTC_TIMESTAMP(3)", "publish_time=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY)", "status=0,publish_time=NULL")) {
            jdbc.update("UPDATE blog_article SET " + update + " WHERE article_id=?", article);
            code(call("GET", path, token, null), 404, "NOT_FOUND");
            jdbc.update("UPDATE blog_article SET visibility=1,status=1,deleted_at=NULL,publish_time=UTC_TIMESTAMP(3) WHERE article_id=?", article);
        }
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), body("File-Public", "no reference")), 200, "OK");
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
        code(write("PUT", "/admin/articles/" + article, admin, revision(admin, article), body("File-Independent", "![x](" + context(article, file) + ")")), 200, "OK");
        String path = "/admin/articles/" + article + "/files/" + file + "/content";
        code(call("GET", path, author, null), 404, "NOT_FOUND");
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,data_scope) VALUES(800,'context_file_all','查文件','ALL')");
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
        for (String state : List.of("status=0", "status=1,visibility=2,publish_time=UTC_TIMESTAMP(3)", "status=2", "deleted_at=UTC_TIMESTAMP(3)")) {
            jdbc.update("UPDATE blog_article SET " + state + " WHERE article_id=?", article);
            code(call("DELETE", "/admin/files/" + file, token, null), 409, "RESOURCE_IN_USE");
            assertThat(objectPath(file)).exists();
        }
        jdbc.update("UPDATE blog_article SET cover_url=?,deleted_at=NULL WHERE article_id=?", context(article, file), article);
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
            jdbc.update("INSERT INTO blog_article(article_id,author_id,title,slug) VALUES(?,?,?,?)", 80000 + index, userId("file_batch_refs"), "batch", "batch-" + index);
            jdbc.update("INSERT INTO blog_article_content(article_id,markdown_content) VALUES(?,?)", 80000 + index,
                    index == 100 ? "![x](" + context("80100", file) + ")" : "body");
        }
        code(call("DELETE", "/admin/files/" + file, token, null), 409, "RESOURCE_IN_USE");
        jdbc.update("UPDATE blog_article_content SET markdown_content=? WHERE article_id=80100", "`" + context("80100", file) + "`");
        code(call("DELETE", "/admin/files/" + file, token, null), 200, "OK");
        code(call("DELETE", "/admin/files/" + file, token, null), 404, "NOT_FOUND");
    }

    @Test
    void uploadDatabaseAndAuditFailuresRemoveNewObjectsAndMetadata() throws Exception {
        String token = actor("file_upload_rollback", 1);
        List<Path> before = uploadArtifacts();
        for (String table : List.of("blog_file", "sys_operation_log")) {
            jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON " + table + " FOR EACH ROW "
                    + (table.equals("sys_operation_log") ? "BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated file rollback'; END IF; END"
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
        jdbc.execute("CREATE TRIGGER part7_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.result=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated delete rollback'; END IF; END");
        code(call("DELETE", "/admin/files/" + id, token, null), 503, "SERVICE_UNAVAILABLE");
        assertThat(object).doesNotExist();
        code(call("GET", "/admin/files/" + id, token, null), 200, "OK");
        code(call("GET", "/admin/files/" + id + "/content", token, null), 503, "SERVICE_UNAVAILABLE");
        jdbc.execute("DROP TRIGGER part7_fail");
        code(call("DELETE", "/admin/files/" + id, token, null), 200, "OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_target='blog:file' AND operation_type='delete' AND result=1 AND target_id=?", Integer.class, id)).isEqualTo(1);
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
                            case "permission" -> jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_id=62");
                            case "scope" -> jdbc.update("UPDATE blog_file SET uploader_id=? WHERE file_id=?", userId("file_wait_admin"), id);
                        }
                    }, true);
            code(result, change.equals("token") ? 401 : change.equals("permission") ? 403 : 404,
                    change.equals("token") ? "UNAUTHENTICATED" : change.equals("permission") ? "FORBIDDEN" : "NOT_FOUND");
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
                () -> jdbc.update("UPDATE blog_article_content SET markdown_content=? WHERE article_id=?", "![x](" + context(article, id) + ")", article), true);
        code(result, 409, "RESOURCE_IN_USE");
        assertThat(objectPath(id)).exists();
    }

    @Test
    void waitingPublicAttachmentSeesUnpublishAndRemovedReference() throws Exception {
        String token = actor("file_wait_public", 1);
        String id = uploaded(token, "wait-public.png");
        String article = create(token, "File-Wait-Public", "body");
        code(write("PUT", "/admin/articles/" + article, token, revision(token, article), body("File-Wait-Public", "![x](" + context(article, id) + ")")), 200, "OK");
        code(status(token, article, 1), 200, "OK");
        String path = context(article, id).substring("/api/v1".length());
        for (String change : List.of("state", "reference")) {
            code(waitingInteraction("blog_article", "SELECT * FROM blog_article WHERE article_id=? FOR UPDATE", article,
                    () -> call("GET", path, null, null), () -> {
                        if (change.equals("state")) { jdbc.update("UPDATE blog_article SET status=2 WHERE article_id=?", article); }
                        else { jdbc.update("UPDATE blog_article_content SET markdown_content='' WHERE article_id=?", article); }
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
            code(call("GET", "/blog/articles/" + invalid + "/files/" + id + "/content", null, null), 400, "VALIDATION_ERROR");
            code(call("GET", "/blog/articles/1/files/" + invalid + "/content", null, null), 400, "VALIDATION_ERROR");
        }
    }

    @Test
    void uploadWaitingForUserLockRechecksDisabledAccountAndToken() throws Exception {
        for (String change : List.of("account", "token")) {
            String name = "file_upload_wait_" + change;
            String token = actor(name, 2);
            MvcResult result = waitingInteraction("sys_user", "SELECT * FROM sys_user WHERE user_id=? FOR UPDATE", userId(name),
                    () -> upload(token, "wait.png", "image/png", image("png")), () -> {
                        if (change.equals("account")) { jdbc.update("UPDATE sys_user SET status=0 WHERE user_id=?", userId(name)); }
                        else { sessions.revokeAll(Long.parseLong(userId(name))); }
                    }, true);
            code(result, change.equals("account") ? 403 : 401, change.equals("account") ? "ACCOUNT_DISABLED" : "UNAUTHENTICATED");
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
            var deleting = executor.submit(() -> { await(start); return call("DELETE", "/admin/files/" + file, uploader, null); });
            var referencing = executor.submit(() -> { await(start); return write("PUT", "/admin/articles/" + article, editor, version,
                    body("File-Real-Race", "![x](" + context(article, file) + ")")); });
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
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), extension.equals("png") ? "png" : "jpeg", output);
        return output.toByteArray();
    }

    /** multipart 走真实 MVC 和业务，不 mock 文件存储或元数据。 */
    private MvcResult upload(String token, String name, String type, byte[] bytes) throws Exception {
        var builder = multipart("/api/v1/admin/files").file(new MockMultipartFile("file", name, type, bytes));
        builder.contextPath("/api/v1").servletPath("/admin/files");
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
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
        return files.getLocalRoot().resolve(jdbc.queryForObject("SELECT object_key FROM blog_file WHERE file_id=?", String.class, id));
    }

    /** 只比较本章随机对象和临时文件，保留较早测试的固定文件夹及历史产物。 */
    private List<Path> uploadArtifacts() throws Exception {
        try (var paths = Files.list(files.getLocalRoot())) {
            return paths.filter(path -> path.getFileName().toString().startsWith(".upload-")
                    || path.getFileName().toString().matches("[A-Za-z0-9_-]{40,}\\.(png|jpg|jpeg)")).sorted().toList();
        }
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
        MvcResult registered = call("POST", "/auth/register", null, json.writeValueAsString(Map.of("username", username, "password", PASSWORD, "nickname", "叶")));
        code(registered, 201, "OK");
        if (role != 3) {
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", data(registered).get("userId").asString(), role);
        }
        MvcResult logged = call("POST", "/auth/login", null, json.writeValueAsString(Map.of("username", username, "password", PASSWORD)));
        code(logged, 200, "OK");
        return data(logged).get("accessToken").asString();
    }

    private String userId(String username) { return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=?", String.class, username); }

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
        var builder = req(method, path);
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        if (body != null) { builder.contentType("application/json").content(body); }
        return mvc.perform(builder).andReturn();
    }

    private MvcResult write(String method, String path, String token, String revision, Object body) throws Exception {
        var builder = req(method, path);
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        if (revision != null) { builder.header("If-Revision", revision); }
        if (body != null) { builder.contentType("application/json").content(json.writeValueAsString(body)); }
        return mvc.perform(builder).andReturn();
    }

    private MockHttpServletRequestBuilder req(String method, String path) {
        return request(HttpMethod.valueOf(method), "/api/v1" + path).contextPath("/api/v1").servletPath(path.split("\\?", 2)[0]);
    }

    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data"); }

    private void code(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("code").asString()).isEqualTo(code);
    }

    private void seedDictionary() {
        jdbc.update("INSERT INTO blog_category(category_id,category_name,slug) VALUES(700,'分类','category')");
        jdbc.update("INSERT INTO blog_tag(tag_id,tag_name,slug) VALUES(701,'标签一','tag-one'),(702,'标签二','tag-two')");
    }

    private void comment(long commentId, String article, long user, Long parent, int status) {
        jdbc.update("INSERT INTO blog_comment(comment_id,article_id,user_id,parent_id,content,status) VALUES(?,?,?,?,?,?)", commentId, article, user, parent, "评论", status);
    }

    private void addFile(long id, String uploader, String key) throws Exception {
        Files.write(files.getLocalRoot().resolve(key), new byte[]{1, 2, 3});
        jdbc.update("INSERT INTO blog_file(file_id,uploader_id,original_name,storage_type,bucket,object_key,mime_type,size_bytes) VALUES(?,?,?,'local','',?,'image/png',3)", id, uploader, key, key);
    }

    private String context(String article, String file) { return "/api/v1/blog/articles/" + article + "/files/" + file + "/content"; }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("测试锁未释放"); }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /** 实际看到 InnoDB 锁等待才继续，不能睡一会儿就假定请求已经等到目标锁。 */
    private void awaitLockWait(String table) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_NAME=?", Integer.class, table);
            if (count > 0) { return; }
            Thread.sleep(10);
        }
        throw new AssertionError("没有观察到目标InnoDB锁等待：" + table);
    }
}
