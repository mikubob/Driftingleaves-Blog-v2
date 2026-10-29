package com.xuan.config;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import com.xuan.properties.AuthProperties;
import com.xuan.service.IUserService;
import com.xuan.service.OAuthProviderGateway;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.net.URI;
import java.util.List;
import java.util.Set;

/** 精确公开白名单、Sa-Token 注解与后台双权限门禁，默认拒绝匿名业务请求。 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {
    private static final Set<String> PUBLIC = Set.of("POST /auth/register", "POST /auth/login", "POST /auth/logout",
            "GET /auth/oauth/providers", "POST /auth/oauth/exchange");
    private final IUserService users;
    private final OAuthProviderGateway providers;
    private final AuthProperties properties;
    private final ObjectProvider<HttpServletRequest> requests;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先检查当前身份及后台入口；SaInterceptor 默认先执行注解，须拆开保证错误顺序。
        registry.addInterceptor(new SaInterceptor(handler -> guard(requests.getObject(), handler))
                .isAnnotation(false)).addPathPatterns("/**");
        // 2. 身份有效后再执行具体业务权限注解，不让权限错误覆盖账号状态或会话失效。
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
        // 3. 后台处理方法还必须声明业务权限，不能仅凭 admin:access 进入业务。
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                                     Object handler) {
                if (request.getServletPath().startsWith("/admin/") && handler instanceof HandlerMethod method
                        && !AnnotatedElementUtils.hasAnnotation(method.getMethod(), SaCheckPermission.class)
                        && !AnnotatedElementUtils.hasAnnotation(method.getBeanType(), SaCheckPermission.class)) {
                    throw new BusinessException(403, "FORBIDDEN", "后台入口未声明业务权限");
                }
                return true;
            }
        }).addPathPatterns("/admin/**");
    }

    private void guard(HttpServletRequest request, Object handler) {
        // 1. 放行错误分派和预检；跨域是否允许由前置 CORS 过滤器判断。
        if (request.getDispatcherType() == DispatcherType.ERROR || "OPTIONS".equals(request.getMethod())) {
            return;
        }
        // 2. 按 HTTP 方法与完整路径匹配公开入口，不整段放开 auth 或 blog。
        String path = request.getServletPath();
        String route = request.getMethod() + " " + path;
        if (PUBLIC.contains(route)) {
            return;
        }
        // 3. OAuth 动态路径还需平台白名单；BIND 条件鉴权留在协调服务中执行。
        if (("POST".equals(request.getMethod()) && path.matches("/auth/oauth/[^/]+/authorize"))
                || ("GET".equals(request.getMethod()) && path.matches("/auth/oauth/[^/]+/callback"))) {
            providers.requireEnabled(path.split("/")[3]);
            return;
        }
        // 4. 其余入口实时检查登录及账号状态，后台另需有效的 admin:access。
        users.requireCurrent();
        if (path.startsWith("/admin/")) {
            StpUtil.checkPermission("admin:access");
        }
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(JsonMapper json) {
        // 1. 启动时核对每个配置项都是具体 origin，拒绝通配符、凭据和路径。
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : properties.getAllowedOrigins()) {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath() == null || uri.getPath().isEmpty())) {
                throw new IllegalArgumentException("allowed-origins必须是明确的HTTP(S) origin");
            }
        }
        // 2. 只开放声明的方法及请求头，暴露版本、跳转和限流响应头以支持前端。
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Revision"));
        config.setExposedHeaders(List.of("X-Revision", "Location", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);
        // 3. 为全部路径注册 CORS，并将跨域拒绝转换为统一 JSON 错误。
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        CorsFilter cors = new CorsFilter(source);
        cors.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().setCacheControl("no-store");
                response.getBody().write(json.writeValueAsString(Result.error("FORBIDDEN", "跨域请求不被允许"))
                        .getBytes(StandardCharsets.UTF_8));
                response.flush();
            }
        });
        // 4. 将 CORS 放在鉴权和请求体处理之前，使合法预检无需本站 Token。
        FilterRegistrationBean<CorsFilter> filter = new FilterRegistrationBean<>(cors);
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return filter;
    }
}
