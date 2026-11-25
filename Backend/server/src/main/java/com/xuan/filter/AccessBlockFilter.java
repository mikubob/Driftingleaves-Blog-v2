package com.xuan.filter;

import com.xuan.constant.ErrorCode;
import com.xuan.constant.HttpConstants;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.VisitorMapper;
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
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 在读取请求体和执行业务前检查客户端 IP 封禁。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class AccessBlockFilter extends OncePerRequestFilter {

    private final NotFoundRateLimiter guard;
    private final VisitorMapper visitors;
    private final AuthProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    /**
     * 跳过 OPTIONS 预检，其他请求都检查封禁，包括公开查询和 WebSocket 握手。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod());
    }

    /**
     * 检查 IP 封禁，被封禁或封禁检查失败时返回统一错误，不继续执行业务。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String ip = IpUtils.clientIp(request, properties.getTrustedProxies());
            guard.check(ip);
            // 手动封禁保留后台入口，管理员可以从同一网络解封。
            if (!request.getServletPath().startsWith("/admin/")
                    && visitors.hasActiveIpBlock(ip, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
                throw new BusinessException(403, ErrorCode.ACCESS_BLOCKED, "当前 IP 已被封禁");
            }
        } catch (DataAccessException exception) {
            reject(response, new BusinessException(503, ErrorCode.SERVICE_UNAVAILABLE, "请求保护暂不可用"));
            return;
        } catch (BusinessException exception) {
            reject(response, exception);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 返回封禁或检查失败的统一 JSON，只有自动封禁带重试时间。
     */
    private void reject(HttpServletResponse response, BusinessException exception) throws IOException {
        response.setStatus(exception.getStatus());
        response.setContentType(HttpConstants.APPLICATION_JSON_UTF8);
        response.setHeader(HttpConstants.HEADER_CACHE_CONTROL, HttpConstants.CACHE_NO_STORE);
        response.setHeader(HttpConstants.HEADER_REFERRER_POLICY, HttpConstants.REFERRER_NO_REFERRER);
        if (exception.getRetryAfter() != null) {
            response.setHeader(HttpConstants.HEADER_RETRY_AFTER, exception.getRetryAfter().toString());
        }
        response.getWriter().write(json.writeValueAsString(
                Result.error(exception.getCode(), exception.getMessage())));
    }
}
