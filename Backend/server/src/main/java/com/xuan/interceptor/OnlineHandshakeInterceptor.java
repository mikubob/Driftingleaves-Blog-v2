package com.xuan.interceptor;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.RedisRateLimiter;
import com.xuan.result.Result;
import com.xuan.utils.IpUtils;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 校验在线统计的 WebSocket 握手，只开放指定地址，不从 Cookie 或请求头获取登录身份。 */
@Component
@RequiredArgsConstructor
public class OnlineHandshakeInterceptor implements HandshakeInterceptor {
    private final AuthProperties auth;
    private final RedisRateLimiter limiter;
    private final JsonMapper json;

    /** 检查握手参数、来源和请求频率，通过后保存站点信息。 */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) throws Exception {
        try {
            var headers = request.getHeaders();
            if (request.getURI().getRawQuery() != null || !"websocket".equalsIgnoreCase(headers.getUpgrade())
                    || !headers.getConnection().stream().anyMatch("upgrade"::equalsIgnoreCase)
                    || headers.containsHeader("Authorization") || headers.containsHeader("Sec-WebSocket-Protocol")) {
                throw new BusinessException(400, "VALIDATION_ERROR", "请使用约定的 WebSocket 握手");
            }
            String origin = headers.getOrigin();
            if (origin == null || !auth.getAllowedOrigins().contains(origin)) {
                throw new BusinessException(403, "FORBIDDEN", "在线统计来源不被允许");
            }
            var servlet = ((ServletServerHttpRequest) request).getServletRequest();
            String ip = IpUtils.clientIp(servlet, auth.getTrustedProxies());
            limiter.hit(List.of(auth.getRedisPrefix() + "rate:online:handshake:" + TokenUtils.digest(ip)), 60, 120);
            attributes.put("onlineOrigin", origin);
            attributes.put("onlineSite", servlet.getServletPath().split("/")[1]);
            return true;
        } catch (BusinessException error) {
            reject(response, error);
            return false;
        } catch (RuntimeException error) {
            reject(response, new BusinessException(503, "SERVICE_UNAVAILABLE", "在线连接暂不可用"));
            return false;
        }
    }

    /** 返回握手失败结果，触发限流时附上等待秒数。 */
    private void reject(ServerHttpResponse response, BusinessException error) throws Exception {
        response.setStatusCode(HttpStatusCode.valueOf(error.getStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        if (error.getRetryAfter() != null) {
            response.getHeaders().set("Retry-After", error.getRetryAfter().toString());
        }
        response.getBody().write(json.writeValueAsString(Result.error(error.getCode(), error.getMessage()))
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 握手结束时不调整在线人数，连接尚未加入统计。 */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {
        // 尚未登记在线成员，无须在握手失败时回收人数。
    }
}
