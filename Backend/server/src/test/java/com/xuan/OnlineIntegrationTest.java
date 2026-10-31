package com.xuan;

import cn.dev33.satoken.stp.StpUtil;
import com.xuan.exception.BusinessException;
import com.xuan.session.SessionManager;
import com.xuan.store.OnlineTicketStore;
import com.xuan.utils.TokenUtils;
import com.xuan.websocket.OnlineWebSocketHandler;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实 HTTP/原生 WebSocket 与临时 MySQL/Redis；不连接开发或业务服务。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(OnlineIntegrationTest.TimeConfig.class)
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class OnlineIntegrationTest {
    private static final String ORIGIN = Boolean.getBoolean("test.online.browser")
            ? "http://127.0.0.1:25174" : "http://127.0.0.1:5174";
    private static IsolatedServices services;
    @Value("${local.server.port}")
    int port;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    SessionManager sessions;
    @Autowired
    OnlineTicketStore tickets;
    @Autowired
    OnlineWebSocketHandler handler;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    MutableClock clock;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Client> clients = new ArrayList<>();

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
        registry.add("app.auth.redis-prefix", () -> "dl:online-test:");
        registry.add("app.auth.allowed-origins", () -> ORIGIN + (Boolean.getBoolean("test.online.browser")
                ? ",http://127.0.0.1:25173,http://127.0.0.1:25175" : ",http://127.0.0.1:5173,http://127.0.0.1:5175"));
        registry.add("app.auth.frontend-complete-url", () -> ORIGIN + "/oauth/complete");
        registry.add("app.sites.blog-base-url", () -> ORIGIN);
        registry.add("app.sites.home-base-url", () -> Boolean.getBoolean("test.online.browser") ? "http://127.0.0.1:25173" : "http://127.0.0.1:5173");
        registry.add("app.sites.cv-base-url", () -> Boolean.getBoolean("test.online.browser") ? "http://127.0.0.1:25175" : "http://127.0.0.1:5175");
        registry.add("app.sites.api-base-url", () -> ORIGIN + "/api/v1");
        registry.add("app.files.local-root", () -> services.directory.resolve("uploads").toString());
    }

    @BeforeEach
    void reset() {
        clock.offset.set(0);
        try (var connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        jdbc.update("DELETE FROM sys_user");
    }

    @AfterEach
    void disconnect() throws Exception {
        for (Client client : clients) {
            if (!client.closed.isDone()) {
                client.socket.sendClose(1000, "完成").get(5, TimeUnit.SECONDS);
                client.closed.get(5, TimeUnit.SECONDS);
            }
        }
        clients.clear();
        clock.offset.set(0);
    }

    @AfterAll
    static void stop(@Autowired ConfigurableApplicationContext context) {
        context.getBean(OnlineWebSocketHandler.class).stop();
        context.getBean(LettuceConnectionFactory.class).destroy();
        context.getBean(HikariDataSource.class).close();
        if (services != null) {
            services.close();
        }
    }

    @Test
    void ticketBoundaryValidatesOriginFieldsIdentityAndDoesNotRecordPageViews() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class);
        var response = issue(null, "browser-0000000001");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        String ticket = tree(response).path("data").path("ticket").asString();
        assertThat(ticket).matches("[A-Za-z0-9_-]{43}");
        assertThat(redis.getExpire("dl:online-test:online:ticket:" + TokenUtils.digest(ticket))).isBetween(28L, 30L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_visitor", Integer.class)).isZero();
        assertThat(issue("invalid-token", "browser-0000000001").statusCode()).isEqualTo(401);
        String body = payload("browser-0000000001");
        for (String invalid : List.of(body.replace("blog", "admin"), body.replace("\"/\"", "\"/?ticket=secret\""),
                body.replace("}", ",\"userId\":\"1\"}"), body.replace("}", ",\"siteCode\":\"blog\"}"),
                body.replace("\"browser-0000000001\"", "12"), "{}")) {
            assertThat(post("/analytics/online/tickets", invalid, ORIGIN, null).statusCode()).isEqualTo(400);
        }
        assertThat(post("/analytics/online/tickets?userId=1", body, ORIGIN, null).statusCode()).isEqualTo(400);
        assertThat(post("/analytics/online/tickets", body, null, null).statusCode()).isEqualTo(403);
        assertThat(post("/analytics/online/tickets", body, "http://127.0.0.1:5173", null).statusCode()).isEqualTo(403);
        String disabled = token("disabled", -301_000);
        jdbc.update("UPDATE sys_user SET status=0 WHERE username='disabled'");
        assertThat(tree(issue(disabled, "browser-0000000001")).path("code").asString()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void redisCompareAndDeleteIsSingleUseUnderConcurrencyAndExpiry() throws Exception {
        String ticket = tickets.issue("isolated-payload");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            Callable<Boolean> consume = () -> {
                ready.countDown();
                go.await();
                try {
                    tickets.consume(ticket, "isolated-payload");
                    return true;
                } catch (BusinessException error) {
                    assertThat(error.getCode()).isEqualTo("ONLINE_TICKET_INVALID");
                    return false;
                }
            };
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThatThrownBy(() -> tickets.read(ticket)).isInstanceOf(BusinessException.class);
        String expired = tickets.issue("expired");
        redis.expire("dl:online-test:online:ticket:" + TokenUtils.digest(expired), Duration.ofMillis(50));
        Thread.sleep(80);
        assertThatThrownBy(() -> tickets.read(expired)).isInstanceOf(BusinessException.class);
        String wrong = tickets.issue("original");
        assertThatThrownBy(() -> tickets.consume(wrong, "different-purpose")).isInstanceOf(BusinessException.class);
        assertThat(tickets.read(wrong)).isEqualTo("original");
    }

    @Test
    void realSocketsDeduplicateUsersAndGuestsAndRestoreAnonymousKeyOnLogout() throws Exception {
        String user = token("account-a", -301_000);
        String otherDevice = new TransactionTemplate(transactions).execute(status ->
                sessions.create(userId("account-a"), clock.millis(), "OAUTH"));
        String secondUser = token("account-b", 0);
        Client guest = joined(null, "browser-0000000001");
        assertCounts(guest.next(), "1", "0", "1");
        Client duplicate = joined(null, "browser-0000000001");
        assertCounts(duplicate.next(), "1", "0", "1");
        assertThat(guest.messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
        Client logged = joined(user, "browser-0000000001");
        assertCounts(logged.next(), "1", "1", "0");
        assertCounts(guest.next(), "1", "1", "0");
        duplicate.next();
        Client sameAccount = joined(otherDevice, "browser-0000000002");
        assertCounts(sameAccount.next(), "1", "1", "0");
        Client anotherAccount = joined(secondUser, "browser-0000000001");
        assertCounts(anotherAccount.next(), "2", "2", "0");
        for (Client client : List.of(guest, duplicate, logged, sameAccount)) {
            client.next();
        }
        StpUtil.logoutByTokenValue(user);
        clock.offset.addAndGet(25_000);
        handler.sweep();
        assertThat(logged.next().path("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(logged.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(guest.messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
        jdbc.update("UPDATE sys_user SET status=0 WHERE username='account-b'");
        clock.offset.addAndGet(25_000);
        handler.sweep();
        assertThat(anotherAccount.next().path("code").asString()).isEqualTo("ACCOUNT_DISABLED");
        assertCounts(guest.next(), "2", "1", "1");
        assertCounts(sameAccount.next(), "2", "1", "1");
    }

    @Test
    void joinRechecksOriginalTokenAndOriginWithoutConsumingWrongOriginTicket() throws Exception {
        String user = token("revoked-before-join", 0);
        String ticket = ticket(user, "browser-0000000001");
        StpUtil.logoutByTokenValue(user);
        Client revoked = connect(ORIGIN);
        revoked.send("{\"type\":\"join\",\"ticket\":\"" + ticket + "\"}");
        assertThat(revoked.next().path("code").asString()).isEqualTo("UNAUTHENTICATED");
        String anonymous = ticket(null, "browser-0000000002");
        Client foreign = connect("http://127.0.0.1:5173");
        foreign.send("{\"type\":\"join\",\"ticket\":\"" + anonymous + "\"}");
        assertThat(foreign.next().path("code").asString()).isEqualTo("ONLINE_TICKET_INVALID");
        assertThat(tickets.read(anonymous)).isNotBlank();
        Client valid = connect(ORIGIN);
        valid.send("{\"type\":\"join\",\"ticket\":\"" + anonymous + "\"}");
        assertCounts(valid.next(), "1", "0", "1");
        Client replay = connect(ORIGIN);
        replay.send("{\"type\":\"join\",\"ticket\":\"" + anonymous + "\"}");
        assertThat(replay.next().path("code").asString()).isEqualTo("ONLINE_TICKET_INVALID");
    }

    @Test
    void heartbeatTimeoutCannotBeRevivedAndJoinDeadlineIsEnforced() throws Exception {
        Client client = joined(null, "browser-0000000001");
        client.next();
        client.send("{\"type\":\"ping\"}");
        assertThat(client.next().path("type").asString()).isEqualTo("pong");
        clock.offset.addAndGet(75_000);
        client.send("{\"type\":\"ping\"}");
        assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4000);
        Client waiting = connect(ORIGIN);
        assertThat(waiting.closed.get(7, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(waiting.next().path("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void ordinaryGetQueryAndHeaderIdentityCannotBypassHandshake() throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri("/ws/online")).header("Origin", ORIGIN).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(tree(response).path("code").asString()).isEqualTo("VALIDATION_ERROR");
        for (String suffix : List.of("?token=secret", "?ticket=secret")) {
            assertThatThrownBy(() -> http.newWebSocketBuilder().header("Origin", ORIGIN)
                    .buildAsync(wsUri(suffix), new Client()).join()).hasCauseInstanceOf(WebSocketHandshakeException.class);
        }
        assertThatThrownBy(() -> http.newWebSocketBuilder().header("Origin", ORIGIN)
                .header("Authorization", "Bearer secret").buildAsync(wsUri(""), new Client()).join())
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
        assertThatThrownBy(() -> http.newWebSocketBuilder().buildAsync(wsUri(""), new Client()).join())
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void protocolBoundariesRejectDuplicatesBinaryOversizeAndRateExcess() throws Exception {
        for (String message : List.of("{\"type\":\"ping\"}", "{\"type\":\"join\",\"ticket\":\"bad\",\"userId\":\"1\"}",
                "{\"type\":\"join\",\"type\":\"join\",\"ticket\":\"bad\"}", "{\"type\":12}", "[]", "")) {
            Client client = connect(ORIGIN);
            client.send(message);
            assertThat(client.next().path("code").asString()).isEqualTo("VALIDATION_ERROR");
            assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        }
        Client binary = connect(ORIGIN);
        binary.socket.sendBinary(java.nio.ByteBuffer.wrap(new byte[]{1}), true).join();
        assertThat(binary.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        Client large = connect(ORIGIN);
        large.send("x".repeat(1025));
        assertThat(large.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1009);
        Client limited = joined(null, "browser-0000000001");
        limited.next();
        for (int index = 0; index < 29; index++) {
            limited.send("{\"type\":\"ping\"}");
            assertThat(limited.next().path("type").asString()).isEqualTo("pong");
        }
        limited.send("{\"type\":\"ping\"}");
        assertThat(limited.next().path("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(limited.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }

    @Test
    void ticketLimitsCountEachDimensionOnceAndReturnRetryAfter() throws Exception {
        for (int index = 0; index < 30; index++) {
            assertThat(issue(null, "browser-0000000001").statusCode()).isEqualTo(201);
        }
        var limited = issue(null, "browser-0000000001");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(redis.opsForValue().get("dl:online-test:rate:online:ticket:ip:" + TokenUtils.digest("127.0.0.1")))
                .isEqualTo("31");
        for (int index = 31; index < 120; index++) {
            assertThat(issue(null, "browser-000000" + String.format("%04d", index)).statusCode()).isEqualTo(201);
        }
        assertThat(issue(null, "browser-0000000121").statusCode()).isEqualTo(429);
    }

    @Test
    void originalReferenceDoesNotRequireRecentAuthenticationButKeepsFixedExpiry() {
        String token = token("older-authentication", -301_000);
        long user = userId("older-authentication");
        long expiresAt = clock.millis() + sessions.remainingSeconds(token) * 1000;
        sessions.requireOriginalActive(user, TokenUtils.digest(token), expiresAt);
        assertThatThrownBy(() -> sessions.originalToken(user, TokenUtils.digest(token)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo("REAUTH_REQUIRED"));
        clock.offset.addAndGet(7_200_000);
        assertThatThrownBy(() -> sessions.requireOriginalActive(user, TokenUtils.digest(token), expiresAt))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo("UNAUTHENTICATED"));
    }

    @Test
    @EnabledIfSystemProperty(named = "test.online.browser", matches = "true")
    void browserVerificationWindow() throws Exception {
        var directory = Path.of("../target/verification").toAbsolutePath();
        Files.createDirectories(directory);
        var done = directory.resolve("online-browser.done");
        Files.deleteIfExists(done);
        Files.writeString(directory.resolve("online-browser.json"),
                json.writeValueAsString(Map.of("port", port)));
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(8);
        while (!Files.exists(done) && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(Files.exists(done)).as("浏览器验证完成标记").isTrue();
    }

    private String token(String name, long authenticatedOffset) {
        jdbc.update("INSERT INTO sys_user(username,nickname,status) VALUES(?,?,1)", name, name);
        return new TransactionTemplate(transactions).execute(status ->
                sessions.create(userId(name), clock.millis() + authenticatedOffset, "PASSWORD"));
    }

    private long userId(String name) {
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=?", Long.class, name);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api/v1" + path);
    }

    private URI wsUri(String suffix) {
        return URI.create("ws://127.0.0.1:" + port + "/api/v1/ws/online" + suffix);
    }

    private String payload(String fingerprint) {
        return json.writeValueAsString(Map.of("fingerprint", fingerprint, "siteCode", "blog", "pagePath", "/"));
    }

    private HttpResponse<String> post(String path, String body, String origin, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> issue(String token, String fingerprint) throws Exception {
        return post("/analytics/online/tickets", payload(fingerprint), ORIGIN, token);
    }

    private JsonNode tree(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private String ticket(String token, String fingerprint) throws Exception {
        var response = issue(token, fingerprint);
        assertThat(response.statusCode()).isEqualTo(201);
        return tree(response).path("data").path("ticket").asString();
    }

    private Client connect(String origin) {
        Client client = new Client();
        client.socket = http.newWebSocketBuilder().header("Origin", origin).buildAsync(wsUri(""), client).join();
        clients.add(client);
        return client;
    }

    private Client joined(String token, String fingerprint) throws Exception {
        String ticket = ticket(token, fingerprint);
        Client client = connect(ORIGIN);
        client.send(json.writeValueAsString(Map.of("type", "join", "ticket", ticket)));
        return client;
    }

    private void assertCounts(JsonNode message, String total, String users, String guests) {
        assertThat(message.path("type").asString()).isEqualTo("online.count");
        JsonNode data = message.path("data");
        assertThat(data.path("onlineCount").asString()).isEqualTo(total);
        assertThat(data.path("userCount").asString()).isEqualTo(users);
        assertThat(data.path("guestCount").asString()).isEqualTo(guests);
        assertThat(data.path("serverTime").asString()).matches(".*\\.\\d{3}Z");
        assertThat(data.size()).isEqualTo(4);
    }

    private class Client implements WebSocket.Listener {
        WebSocket socket;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder fragments = new StringBuilder();
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence text, boolean last) {
            fragments.append(text);
            if (last) {
                messages.add(fragments.toString());
                fragments.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) {
            closed.complete(status);
            return null;
        }
        void send(String text) {
            socket.sendText(text, true).join();
        }
        JsonNode next() throws Exception {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("服务端消息").isNotNull();
            return json.readTree(message);
        }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock onlineClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        final AtomicLong offset = new AtomicLong();
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.now().plusMillis(offset.get());
        }
    }
}
