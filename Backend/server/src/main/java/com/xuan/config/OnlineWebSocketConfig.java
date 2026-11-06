package com.xuan.config;

import com.xuan.interceptor.OnlineHandshakeInterceptor;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.websocket.OnlineWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/** 配置在线统计连接，Home、Blog、Cv 分别注册 WebSocket 地址，沿用应用路径前缀。 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class OnlineWebSocketConfig implements WebSocketConfigurer {
    private final OnlineWebSocketHandler handler;
    private final OnlineHandshakeInterceptor handshake;
    private final PublicSiteProperties sites;

    /** 注册主页、博客和简历的在线统计连接。 */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        for (String site : List.of("home", "blog", "cv")) {
            registry.addHandler(handler, "/" + site + "/ws/online").addInterceptors(handshake)
                    .setAllowedOrigins(sites.origin(site));
        }
    }
}
