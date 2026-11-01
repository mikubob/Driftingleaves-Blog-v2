package com.xuan.handle;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.NotFoundGuard;
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

/** 统一 JSON 的真实 404 在序列化前计一次，服务故障不累计不存在次数。 */
@RestControllerAdvice
@RequiredArgsConstructor
public class NotFoundResponseAdvice implements ResponseBodyAdvice<Object> {

    private final NotFoundGuard guard;
    private final AuthProperties properties;

    /** 统一响应在写出时再判断，不依赖方法声明是否包着 ResponseEntity。 */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /** 超限时尚未写出响应，可以直接返回封禁状态及剩余秒数。 */
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
