package com.xuan.filter;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.NotFoundRateLimiter;
import com.xuan.result.Result;
import com.xuan.utils.IpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** 过滤被临时封禁的 IP，在跨域检查后、读取请求体和执行业务前执行。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class AccessBlockFilter extends OncePerRequestFilter {

    private final NotFoundRateLimiter guard;
    private final AuthProperties properties;
    private final JsonMapper json;

    /** 跳过 OPTIONS 预检，其他请求都检查封禁，包括公开查询和 WebSocket 握手。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod());
    }

    /** 检查 IP 封禁，被封禁或封禁检查失败时返回统一错误，不继续执行业务。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            guard.check(IpUtils.clientIp(request, properties.getTrustedProxies()));
        } catch (BusinessException exception) {
            response.setStatus(exception.getStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            if (exception.getRetryAfter() != null) {
                response.setHeader("Retry-After", exception.getRetryAfter().toString());
            }
            response.getWriter().write(json.writeValueAsString(
                    Result.error(exception.getCode(), exception.getMessage())));
            return;
        }
        chain.doFilter(request, response);
    }
}
