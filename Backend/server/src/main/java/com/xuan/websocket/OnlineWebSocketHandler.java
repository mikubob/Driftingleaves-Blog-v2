package com.xuan.websocket;

import com.xuan.exception.BusinessException;
import com.xuan.model.OnlineBinding;
import com.xuan.policy.OnlineIdentityPolicy;
import com.xuan.store.OnlineTicketStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 更新在线状态并按连接顺序发送消息，成员锁内不执行票据、身份查询或网络发送。
 * shortcut: 人数与连接仅属于当前进程，多实例部署时需改为共享成员与跨节点广播。
 */
@Component
public class OnlineWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_BUFFER_BYTES = 16 * 1024;

    private final OnlineTicketStore tickets;
    private final OnlineIdentityPolicy identities;
    private final JsonMapper json;
    private final Clock clock;
    private final ExecutorService executor;
    /** 保护成员状态和每个连接的待发队列，异步查询完成后也要拿锁复核。 */
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("online-expiry").factory());
    private Counts published = new Counts(0, 0);
    private boolean stopped;

    /** 初始化在线连接，专用执行器由容器在本组件停止后关闭。 */
    public OnlineWebSocketHandler(OnlineTicketStore tickets, OnlineIdentityPolicy identities,
            JsonMapper json, Clock clock, @Qualifier("onlineExecutor") ExecutorService executor) {
        this.tickets = tickets;
        this.identities = identities;
        this.json = json;
        this.clock = clock;
        this.executor = executor;
    }

    /** 启动定期回收；定时线程只检查内存状态和分派任务，不等待外部查询。 */
    @PostConstruct
    public void start() {
        timer.scheduleWithFixedDelay(this::sweep, 5, 5, TimeUnit.SECONDS);
    }

    /** 登记新连接并设置消息大小限制，五秒内未完成加入就关闭。 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(1024);
        session.setBinaryMessageSizeLimit(1024);
        Member member = new Member(new ConcurrentWebSocketSessionDecorator(session, 5000, SEND_BUFFER_BYTES), clock.millis());
        lock.lock();
        try {
            if (!stopped) {
                members.put(session.getId(), member);
                member.joinTimeout = timer.schedule(() -> expireJoin(session.getId()), 5, TimeUnit.SECONDS);
                return;
            }
        } finally {
            lock.unlock();
        }
        close(member, CloseStatus.GOING_AWAY, null);
    }

    /** 接收消息，检查字段和频率；加入查询异步执行，查询期间不接受第二次加入。 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        lock.lock();
        try {
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
                if ("join".equals(type) && member.binding == null && !member.joining && body.size() == 2
                        && body.path("ticket").isString()) {
                    String ticket = body.path("ticket").asString();
                    member.joining = true;
                    executor.execute(() -> join(member, ticket));
                    return;
                }
                if ("ping".equals(type) && member.binding != null && body.size() == 1) {
                    member.lastSeen = now;
                    if (!enqueue(member, envelope("pong", "OK", "成功", null))) {
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
        } finally {
            lock.unlock();
        }
    }

    /** 拒绝二进制消息，在线统计只接受文本消息。 */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        lock.lock();
        try {
            Member member = members.get(session.getId());
            if (member != null) {
                fail(member, invalidMessage(), CloseStatus.POLICY_VIOLATION);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 移除已关闭的连接，并更新在线人数。 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        lock.lock();
        try {
            Member member = members.get(session.getId());
            if (member != null) {
                discard(member, status);
                publish(null);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 处理连接传输错误，尽量发送错误后关闭连接。 */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        lock.lock();
        try {
            Member member = members.get(session.getId());
            if (member != null) {
                fail(member, unavailable(), CloseStatus.SERVER_ERROR);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 定期回收过期连接，登录身份交给虚拟线程重查，同一连接不重复启动查询。 */
    public void sweep() {
        lock.lock();
        try {
            if (stopped) {
                return;
            }
            long now = clock.millis();
            for (Member member : List.copyOf(members.values())) {
                try {
                    requireAlive(member, now);
                    if (member.binding != null && member.binding.userId() != null && !member.checking
                            && now - member.checkedAt >= 25_000) {
                        member.checking = true;
                        executor.execute(() -> checkIdentity(member));
                    }
                } catch (BusinessException error) {
                    fail(member, error, error.getStatus() == 503 ? CloseStatus.SERVER_ERROR : CloseStatus.POLICY_VIOLATION);
                } catch (RuntimeException error) {
                    fail(member, unavailable(), CloseStatus.SERVER_ERROR);
                }
            }
            publish(null);
        } finally {
            lock.unlock();
        }
    }

    /** 停止定时任务并移除全部成员，释放锁后关闭连接，执行器再由容器等待结束。 */
    @PreDestroy
    public void stop() {
        List<Member> closing;
        lock.lock();
        try {
            stopped = true;
            timer.shutdownNow();
            closing = List.copyOf(members.values());
            members.clear();
            for (Member member : closing) {
                member.joinTimeout.cancel(false);
                member.outbound.clear();
                member.pendingBytes = 0;
                member.closeStatus = CloseStatus.GOING_AWAY;
                member.closeMessage = null;
            }
        } finally {
            lock.unlock();
        }
        for (Member member : closing) {
            close(member, CloseStatus.GOING_AWAY, null);
        }
    }

    /** 在成员锁外核验票据和原身份，消费完成后重新检查连接及五秒加入期限。 */
    private void join(Member member, String ticket) {
        OnlineBinding binding = null;
        BusinessException failure = null;
        try {
            String value = tickets.read(ticket);
            binding = json.readValue(value, OnlineBinding.class);
            if (!binding.origin().equals(member.session.getAttributes().get("onlineOrigin"))
                    || !binding.siteCode().equals(member.session.getAttributes().get("onlineSite"))) {
                throw OnlineTicketStore.invalid();
            }
            identities.requireActive(binding);
            tickets.consume(ticket, value);
        } catch (BusinessException error) {
            failure = error;
        } catch (JacksonException error) {
            failure = invalidMessage();
        } catch (RuntimeException error) {
            failure = unavailable();
        }
        lock.lock();
        try {
            // 查询期间可能已经超时或断开，消费过的票据不恢复，旧任务不能重新登记。
            if (members.get(member.session.getId()) != member) {
                return;
            }
            if (failure != null) {
                fail(member, failure, failure.getStatus() == 503 ? CloseStatus.SERVER_ERROR : CloseStatus.POLICY_VIOLATION);
                return;
            }
            try {
                requireAlive(member, clock.millis());
                if (!member.session.isOpen()) {
                    discard(member, CloseStatus.NORMAL);
                    return;
                }
                member.binding = binding;
                member.lastSeen = clock.millis();
                member.checkedAt = member.lastSeen;
                member.joinTimeout.cancel(false);
                publish(member);
            } catch (BusinessException error) {
                fail(member, error, CloseStatus.POLICY_VIOLATION);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 在成员锁外检查原会话与账号，完成时重查当前连接和心跳期限。 */
    private void checkIdentity(Member member) {
        BusinessException failure = null;
        try {
            identities.requireActive(member.binding);
        } catch (BusinessException error) {
            failure = error;
        } catch (RuntimeException error) {
            failure = unavailable();
        }
        lock.lock();
        try {
            if (members.get(member.session.getId()) != member) {
                return;
            }
            member.checking = false;
            if (failure != null) {
                fail(member, failure, failure.getStatus() == 503 ? CloseStatus.SERVER_ERROR : CloseStatus.POLICY_VIOLATION);
                return;
            }
            try {
                requireAlive(member, clock.millis());
                member.checkedAt = clock.millis();
            } catch (BusinessException error) {
                fail(member, error, CloseStatus.POLICY_VIOLATION);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 检查当前期限，迟到心跳不能恢复在线状态；调用方持有成员锁。 */
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
    private void expireJoin(String id) {
        lock.lock();
        try {
            Member member = members.get(id);
            if (member != null && member.binding == null) {
                fail(member, invalidMessage(), CloseStatus.POLICY_VIOLATION);
            }
        } finally {
            lock.unlock();
        }
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

    /** 持有成员锁按顺序入队；缓冲超限先移除连接，再广播最新人数。 */
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
                    failed |= !enqueue(member, message);
                }
            }
            joining = null;
            if (!failed) {
                return;
            }
        }
    }

    /** 将消息放入当前连接队列，只允许一个发送任务，慢连接不阻塞其他成员。 */
    private boolean enqueue(Member member, String message) {
        TextMessage frame = new TextMessage(message);
        if (member.pendingBytes + frame.getPayloadLength() > SEND_BUFFER_BYTES) {
            discard(member, CloseStatus.SERVER_ERROR);
            return false;
        }
        member.outbound.addLast(frame);
        member.pendingBytes += frame.getPayloadLength();
        startSending(member);
        return true;
    }

    /** 在成员锁内启动发送任务，避免两个线程同时发送同一连接。 */
    private void startSending(Member member) {
        if (!member.sending) {
            member.sending = true;
            executor.execute(() -> drain(member));
        }
    }

    /** 按入队顺序在锁外发送，失败后移除成员并重新发布人数。 */
    private void drain(Member member) {
        while (true) {
            TextMessage message;
            CloseStatus status;
            String error;
            lock.lock();
            try {
                status = member.closeStatus;
                error = member.closeMessage;
                message = member.outbound.pollFirst();
                if (message != null) {
                    member.pendingBytes -= message.getPayloadLength();
                }
                if (status != null || message == null) {
                    member.sending = false;
                }
            } finally {
                lock.unlock();
            }
            if (status != null) {
                close(member, status, error);
                return;
            }
            if (message == null) {
                return;
            }
            try {
                if (!member.session.isOpen()) {
                    throw new IOException("连接已关闭");
                }
                member.session.sendMessage(message);
            } catch (IOException | RuntimeException failure) {
                lock.lock();
                try {
                    discard(member, CloseStatus.SERVER_ERROR);
                    publish(null);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /** 移除成员并安排错误包与关闭操作，已移除的连接不重复处理。 */
    private void fail(Member member, BusinessException error, CloseStatus status) {
        if (members.get(member.session.getId()) != member) {
            return;
        }
        member.closeMessage = envelope("error", error.getCode(), error.getMessage(), null);
        discard(member, status);
        publish(null);
    }

    /** 在成员锁内先移除并清空旧消息，再由该连接的发送任务关闭。 */
    private void discard(Member member, CloseStatus status) {
        if (!members.remove(member.session.getId(), member)) {
            return;
        }
        member.joinTimeout.cancel(false);
        member.outbound.clear();
        member.pendingBytes = 0;
        member.closeStatus = status;
        startSending(member);
    }

    /** 在成员锁外尽量发送错误包并关闭，传输损坏时仍完成状态清理。 */
    private void close(Member member, CloseStatus status, String error) {
        try {
            if (error != null && member.session.isOpen()) {
                member.session.sendMessage(new TextMessage(error));
            }
        } catch (IOException | RuntimeException failure) {
            // 错误包无法送达时仍要关闭连接。
        }
        try {
            member.session.close(status);
        } catch (IOException | RuntimeException failure) {
            // 成员已经移除，关闭失败不能重新登记。
        }
    }

    /** WebSocket 单独增加消息类型，保持 HTTP Result 不变。 */
    private String envelope(String type, String code, String msg, Object data) {
        return json.writeValueAsString(new Envelope(type, code, msg, data, List.of()));
    }

    /** 返回协议错误，不回显客户端发送内容。 */
    private BusinessException invalidMessage() {
        return new BusinessException(400, "VALIDATION_ERROR", "在线消息不符合协议");
    }

    /** 返回依赖错误，不暴露连接或身份信息。 */
    private BusinessException unavailable() {
        return new BusinessException(503, "SERVICE_UNAVAILABLE", "在线连接暂不可用");
    }

    /** 当前三项人数，用于判断是否需要广播。 */
    private record Counts(int users, int guests) {
    }

    /** 发送给前端的在线人数，不包含成员身份。 */
    private record Snapshot(String onlineCount, String userCount, String guestCount, LocalDateTime serverTime) {
    }

    /** 在线消息包络，沿用既有错误字段。 */
    private record Envelope(String type, String code, String msg, Object data, List<Object> errors) {
    }

    /** 单个连接的状态和待发消息，除锁外网络操作外均由成员锁保护。 */
    private static class Member {
        final WebSocketSession session;
        final long openedAt;
        final ArrayDeque<TextMessage> outbound = new ArrayDeque<>();
        long lastSeen;
        long checkedAt;
        long windowAt;
        int messages;
        int pendingBytes;
        OnlineBinding binding;
        ScheduledFuture<?> joinTimeout;
        boolean joining;
        boolean checking;
        boolean sending;
        CloseStatus closeStatus;
        String closeMessage;

        /** 建连时间用于五秒加入期限，业务消息另有自己的窗口。 */
        Member(WebSocketSession session, long now) {
            this.session = session;
            openedAt = now;
            lastSeen = now;
            windowAt = now;
        }
    }
}
