package com.xuan.websocket;

import com.xuan.config.WebJsonConfig;
import com.xuan.model.OnlineBinding;
import com.xuan.store.OnlineTicketStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 使用真实登记和消息处理，仅替换外部票据、身份查询及损坏传输边界。 */
class OnlineWebSocketHandlerTest {
    final OnlineTicketStore tickets = mock(OnlineTicketStore.class);
    final OnlineIdentity identities = mock(OnlineIdentity.class);
    final JsonMapper json;
    final OnlineWebSocketHandler handler;
    int sequence;

    OnlineWebSocketHandlerTest() {
        var builder = JsonMapper.builder();
        new WebJsonConfig().webJsonCustomizer().customize(builder);
        json = builder.build();
        handler = new OnlineWebSocketHandler(tickets, identities, json, Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        handler.stop();
    }

    @Test
    void sendFailureRemovesMemberOnceAndPublishesRecomputedSnapshotInOrder() throws Exception {
        Peer observer = joined("browser-one-00001");
        Peer broken = peer();
        doThrow(new IOException("指定传输故障")).when(broken.session).sendMessage(any());
        join(broken, "browser-two-00002");
        assertThat(observer.counts()).containsExactly(1, 2, 1);
        handler.handleTransportError(broken.session, new IOException());
        handler.afterConnectionClosed(broken.session, CloseStatus.SERVER_ERROR);
        assertThat(observer.counts()).containsExactly(1, 2, 1);
        joined("browser-three-003");
        assertThat(observer.counts()).containsExactly(1, 2, 1, 2);
    }

    @Test
    void concurrentChangesAreSerializedAndRepeatedCloseDoesNotChangeCounts() throws Exception {
        Peer observer = joined("observer-00000001");
        List<Peer> peers = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            peers.add(peer());
        }
        List<String> ticketValues = new ArrayList<>();
        for (int index = 0; index < peers.size(); index++) {
            ticketValues.add(ticket("browser-concurrent-" + index));
        }
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<?>>();
            for (int index = 0; index < peers.size(); index++) {
                Peer peer = peers.get(index);
                String ticket = ticketValues.get(index);
                futures.add(executor.submit(() -> {
                    handler.handleMessage(peer.session, new TextMessage(json.writeValueAsString(
                            Map.of("type", "join", "ticket", ticket))));
                    return null;
                }));
            }
            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertThat(observer.counts()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
            futures.clear();
            for (Peer peer : peers) {
                futures.add(executor.submit(() -> {
                    handler.handleTransportError(peer.session, new IOException());
                    handler.afterConnectionClosed(peer.session, CloseStatus.SERVER_ERROR);
                    return null;
                }));
            }
            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertThat(observer.counts()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
                    12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        }
    }

    @Test
    void dependencyFailureDoesNotRegisterGuestOrFakeSuccessfulIdentity() throws Exception {
        Peer observer = joined("observer-00000001");
        Peer unavailable = peer();
        String ticket = "z".repeat(43);
        when(tickets.read(ticket)).thenThrow(new RedisConnectionFailureException("指定Redis故障"));
        handler.handleMessage(unavailable.session, new TextMessage(json.writeValueAsString(
                Map.of("type", "join", "ticket", ticket))));
        assertThat(unavailable.messages.getFirst().path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        verify(unavailable.session).close(CloseStatus.SERVER_ERROR);
        assertThat(observer.counts()).containsExactly(1);
    }

    @Test
    void closingWhileTicketIsConsumedDoesNotLeakRegisteredState() throws Exception {
        Peer observer = joined("observer-00000001");
        Peer closing = peer();
        String ticket = ticket("closing-00000001");
        doAnswer(invocation -> {
            closing.open.set(false);
            handler.afterConnectionClosed(closing.session, CloseStatus.NORMAL);
            return null;
        }).when(tickets).consume(eq(ticket), anyString());
        handler.handleMessage(closing.session, new TextMessage(json.writeValueAsString(
                Map.of("type", "join", "ticket", ticket))));
        assertThat(observer.counts()).containsExactly(1);
        assertThat(closing.messages).isEmpty();
        joined("next-browser-001");
        assertThat(observer.counts()).containsExactly(1, 2);
    }

    private Peer joined(String fingerprint) throws Exception {
        Peer peer = peer();
        join(peer, fingerprint);
        return peer;
    }

    private void join(Peer peer, String fingerprint) throws Exception {
        handler.handleMessage(peer.session, new TextMessage(json.writeValueAsString(
                Map.of("type", "join", "ticket", ticket(fingerprint)))));
    }

    private String ticket(String fingerprint) {
        String ticket = String.format("%043d", ++sequence);
        when(tickets.read(ticket)).thenReturn(json.writeValueAsString(
                new OnlineBinding("blog", "https://blog.example.test", fingerprint, null, null, 0)));
        return ticket;
    }

    private Peer peer() throws Exception {
        Peer peer = new Peer();
        when(peer.session.getId()).thenReturn("connection-" + ++sequence);
        when(peer.session.getAttributes()).thenReturn(Map.of("onlineOrigin", "https://blog.example.test", "onlineSite", "blog"));
        when(peer.session.isOpen()).thenAnswer(invocation -> peer.open.get());
        doAnswer(invocation -> {
            peer.messages.add(json.readTree(((TextMessage) invocation.getArgument(0)).getPayload()));
            return null;
        }).when(peer.session).sendMessage(any());
        doAnswer(invocation -> {
            peer.open.set(false);
            handler.afterConnectionClosed(peer.session, invocation.getArgument(0));
            return null;
        }).when(peer.session).close(any(CloseStatus.class));
        handler.afterConnectionEstablished(peer.session);
        return peer;
    }

    private static class Peer {
        final WebSocketSession session = mock(WebSocketSession.class);
        final AtomicBoolean open = new AtomicBoolean(true);
        final List<JsonNode> messages = new CopyOnWriteArrayList<>();

        List<Integer> counts() {
            return messages.stream().filter(message -> "online.count".equals(message.path("type").asString()))
                    .map(message -> message.path("data").path("onlineCount").asInt()).toList();
        }
    }
}
