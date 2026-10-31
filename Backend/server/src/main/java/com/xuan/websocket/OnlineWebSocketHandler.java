package com.xuan.websocket;

import com.xuan.exception.BusinessException;
import com.xuan.model.OnlineBinding;
import com.xuan.store.OnlineTicketStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * shortcut: 人数与连接仅属于当前进程，多实例部署时需改为共享成员与跨节点广播。
 * 状态、快照与发送共用一把锁，避免同一连接收到倒序广播；发送仍使用 Spring 的并发会话保护。
 */
@Component
@RequiredArgsConstructor
public class OnlineWebSocketHandler extends TextWebSocketHandler {
    private final OnlineTicketStore tickets;
    private final OnlineIdentity identities;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("online-expiry").factory());
    private Counts published = new Counts(0, 0);

    /** 启动定期回收；每个未加入连接另有五秒截止任务。 */
    @PostConstruct
    public void start() {
        timer.scheduleWithFixedDelay(this::sweep, 5, 5, TimeUnit.SECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(1024);
        session.setBinaryMessageSizeLimit(1024);
        Member member = new Member(new ConcurrentWebSocketSessionDecorator(session, 5000, 16 * 1024), clock.millis());
        members.put(session.getId(), member);
        member.joinTimeout = timer.schedule(() -> expireJoin(session.getId()), 5, TimeUnit.SECONDS);
    }

    /** 首条消息、字段和频率在 WebSocket 自己的信任边界校验。 */
    @Override
    protected synchronized void handleTextMessage(WebSocketSession session, TextMessage message) {
        Member member = members.get(session.getId());
        if (member == null) {
            return;
        }
        try {
            long now = clock.millis();
            if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > 1024) {
                discard(member, CloseStatus.TOO_BIG_TO_PROCESS);
                publish(null);
                return;
            }
            requireAlive(member, now);
            if (now - member.windowAt >= 60_000) {
                member.windowAt = now;
                member.messages = 0;
            }
            if (++member.messages > 30) {
                throw new BusinessException(429, "RATE_LIMITED", "消息过于频繁");
            }
            JsonNode body = json.readTree(message.getPayload());
            if (body == null || !body.isObject() || !body.path("type").isString()) {
                throw invalidMessage();
            }
            String type = body.path("type").asString();
            if ("join".equals(type) && member.binding == null && body.size() == 2
                    && body.path("ticket").isString()) {
                String ticket = body.path("ticket").asString();
                String value = tickets.read(ticket);
                OnlineBinding binding = json.readValue(value, OnlineBinding.class);
                if (!binding.origin().equals(session.getAttributes().get("onlineOrigin"))) {
                    throw OnlineTicketStore.invalid();
                }
                identities.requireActive(binding);
                tickets.consume(ticket, value);
                // Redis 或账号查询期间可能已经断开；登记前重查，消费过的票据不恢复。
                requireAlive(member, clock.millis());
                if (!session.isOpen()) {
                    discard(member, CloseStatus.NORMAL);
                    return;
                }
                member.binding = binding;
                member.lastSeen = clock.millis();
                member.checkedAt = member.lastSeen;
                member.joinTimeout.cancel(false);
                publish(member);
                return;
            }
            if ("ping".equals(type) && member.binding != null && body.size() == 1) {
                member.lastSeen = now;
                if (!send(member, envelope("pong", "OK", "成功", null))) {
                    publish(null);
                }
                return;
            }
            throw invalidMessage();
        } catch (BusinessException error) {
            fail(member, error, error.getStatus() == 503 ? CloseStatus.SERVER_ERROR : CloseStatus.POLICY_VIOLATION);
        } catch (JacksonException error) {
            fail(member, invalidMessage(), CloseStatus.POLICY_VIOLATION);
        } catch (RuntimeException error) {
            fail(member, unavailable(), CloseStatus.SERVER_ERROR);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected synchronized void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Member member = members.get(session.getId());
        if (member != null) {
            fail(member, invalidMessage(), CloseStatus.POLICY_VIOLATION);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Member member = members.remove(session.getId());
        if (member != null) {
            member.joinTimeout.cancel(false);
            publish(null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void handleTransportError(WebSocketSession session, Throwable error) {
        Member member = members.get(session.getId());
        if (member != null) {
            fail(member, unavailable(), CloseStatus.SERVER_ERROR);
        }
    }

    /** 与消息入口使用同一时限，迟到的 ping 不能把成员重新变成在线。 */
    private void requireAlive(Member member, long now) {
        if (member.binding == null && now - member.openedAt >= 5000) {
            throw invalidMessage();
        }
        if (member.binding != null && now - member.lastSeen >= 75_000) {
            discard(member, new CloseStatus(4000, "心跳超时"));
            publish(null);
            throw new BusinessException(400, "VALIDATION_ERROR", "心跳已超时");
        }
    }

    /** 加入期限到达时仍未加入就关闭，不登记任何人数。 */
    private synchronized void expireJoin(String id) {
        Member member = members.get(id);
        if (member != null && member.binding == null) {
            fail(member, invalidMessage(), CloseStatus.POLICY_VIOLATION);
        }
    }

    /** 只重查已加入登录连接；依赖失败关闭连接，不能把它冒充匿名成员。 */
    public synchronized void sweep() {
        long now = clock.millis();
        for (Member member : List.copyOf(members.values())) {
            try {
                requireAlive(member, now);
                if (member.binding != null && member.binding.userId() != null && now - member.checkedAt >= 25_000) {
                    identities.requireActive(member.binding);
                    member.checkedAt = now;
                }
            } catch (BusinessException error) {
                fail(member, error, error.getStatus() == 503 ? CloseStatus.SERVER_ERROR : CloseStatus.POLICY_VIOLATION);
            } catch (RuntimeException error) {
                fail(member, unavailable(), CloseStatus.SERVER_ERROR);
            }
        }
        publish(null);
    }

    /** 从仍登记的连接推导人数，不维护容易重复扣减的独立连接计数。 */
    private Counts counts() {
        Set<Long> users = new HashSet<>();
        Set<String> guests = new HashSet<>();
        Set<String> loggedBrowsers = new HashSet<>();
        for (Member member : members.values()) {
            OnlineBinding binding = member.binding;
            if (binding == null) {
                continue;
            }
            if (binding.userId() == null) {
                guests.add(binding.browserKey());
            } else {
                users.add(binding.userId());
                loggedBrowsers.add(binding.browserKey());
            }
        }
        guests.removeAll(loggedBrowsers);
        return new Counts(users.size(), guests.size());
    }

    /** 发送失败先移除，再生成新快照；其余成员不会收到新快照之后的旧数据。 */
    private void publish(Member joining) {
        while (true) {
            Counts current = counts();
            boolean changed = !published.equals(current);
            published = current;
            if (!changed && joining == null) {
                return;
            }
            var data = new Snapshot(Integer.toString(current.users + current.guests),
                    Integer.toString(current.users), Integer.toString(current.guests),
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            String message = envelope("online.count", "OK", "成功", data);
            boolean failed = false;
            for (Member member : List.copyOf(members.values())) {
                if (member.binding != null && (changed || member == joining)) {
                    failed |= !send(member, message);
                }
            }
            joining = null;
            if (!failed) {
                return;
            }
        }
    }

    /** 发送失败立即移除成员，由调用方重新广播人数。 */
    private boolean send(Member member, String message) {
        try {
            if (!member.session.isOpen()) {
                discard(member, CloseStatus.SERVER_ERROR);
                return false;
            }
            member.session.sendMessage(new TextMessage(message));
            return true;
        } catch (IOException | RuntimeException error) {
            discard(member, CloseStatus.SERVER_ERROR);
            return false;
        }
    }

    /** 尽量先发错误包，再统一关闭和清理连接。 */
    private void fail(Member member, BusinessException error, CloseStatus status) {
        if (!members.containsKey(member.session.getId())) {
            return;
        }
        send(member, envelope("error", error.getCode(), error.getMessage(), null));
        discard(member, status);
        publish(null);
    }

    /** 所有关闭路径先移除，再尝试关闭；close/error 回调重复到达不会再扣人数。 */
    private void discard(Member member, CloseStatus status) {
        if (members.remove(member.session.getId()) == null) {
            return;
        }
        member.joinTimeout.cancel(false);
        try {
            member.session.close(status);
        } catch (IOException | RuntimeException error) {
            // 传输已经损坏，登记状态仍必须清理。
        }
    }

    /** WebSocket 单独增加消息类型，保持 HTTP Result 不变。 */
    private String envelope(String type, String code, String msg, Object data) {
        return json.writeValueAsString(new Envelope(type, code, msg, data, List.of()));
    }

    /** 协议错误不回显客户端发送内容。 */
    private BusinessException invalidMessage() {
        return new BusinessException(400, "VALIDATION_ERROR", "在线消息不符合协议");
    }

    /** 依赖错误只返回固定提示，不暴露连接或身份信息。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "在线连接暂不可用");
    }

    /** 应用关闭时停止检查任务，并释放所有已建立连接。 */
    @PreDestroy
    public synchronized void stop() {
        timer.shutdownNow();
        for (Member member : List.copyOf(members.values())) {
            discard(member, CloseStatus.GOING_AWAY);
        }
    }

    private record Counts(int users, int guests) {
    }

    private record Snapshot(String onlineCount, String userCount, String guestCount, LocalDateTime serverTime) {
    }

    private record Envelope(String type, String code, String msg, Object data, List<Object> errors) {
    }

    private static class Member {
        final WebSocketSession session;
        final long openedAt;
        long lastSeen;
        long checkedAt;
        long windowAt;
        int messages;
        OnlineBinding binding;
        ScheduledFuture<?> joinTimeout;

        /** 建连时间用于五秒加入期限，业务消息另有自己的窗口。 */
        Member(WebSocketSession session, long now) {
            this.session = session;
            openedAt = now;
            lastSeen = now;
            windowAt = now;
        }
    }
}
