package com.xuan.config;

import com.xuan.properties.AuthProperties;
import com.xuan.websocket.OnlineHandshake;
import com.xuan.websocket.OnlineWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 复用应用 context-path，Home、Blog、Cv 使用各自端前缀的在线入口。 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class OnlineWebSocketConfig implements WebSocketConfigurer {
    private final OnlineWebSocketHandler handler;
    private final OnlineHandshake handshake;
    private final AuthProperties auth;

    /** {@inheritDoc} */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/home/ws/online", "/blog/ws/online", "/cv/ws/online").addInterceptors(handshake)
                .setAllowedOrigins(auth.getAllowedOrigins().toArray(String[]::new));
    }
}
