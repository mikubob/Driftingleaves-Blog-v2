package com.xuan.handle;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.NotFoundRateLimiter;
import com.xuan.result.Result;
import com.xuan.utils.IpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 记录接口返回的 404，在输出 JSON 前计数，服务故障不计入。 */
@RestControllerAdvice
@RequiredArgsConstructor
public class NotFoundResponseAdvice implements ResponseBodyAdvice<Object> {

    private final NotFoundRateLimiter guard;
    private final AuthProperties properties;

    /** 在响应写出时检查状态，不受方法是否返回 ResponseEntity 影响。 */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /** 检查 404 次数，超限时改为返回封禁状态和剩余秒数。 */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(body instanceof Result<?>) || !(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)
                || servletResponse.getServletResponse().getStatus() != 404
                || "OPTIONS".equals(servletRequest.getServletRequest().getMethod())) {
            return body;
        }
        try {
            guard.record(IpUtils.clientIp(servletRequest.getServletRequest(), properties.getTrustedProxies()));
        } catch (BusinessException exception) {
            servletResponse.getServletResponse().setStatus(exception.getStatus());
            if (exception.getRetryAfter() != null) {
                response.getHeaders().set("Retry-After", exception.getRetryAfter().toString());
            }
            return Result.error(exception.getCode(), exception.getMessage());
        }
        return body;
    }
}
