package com.xuan.config;

import cn.hutool.core.util.StrUtil;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.xuan.client.OAuthProviderClient;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.result.Result;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 配置接口登录和权限检查，公开接口单独放行，后台同时检查入口和操作权限。
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {

    private static final Set<String> PUBLIC = Set.of(
            "POST /admin/security/not-found", "POST /blog/security/not-found",
            "GET /admin/auth/captcha", "GET /blog/auth/captcha",
            "POST /admin/auth/register", "POST /admin/auth/login", "POST /admin/auth/logout",
            "GET /admin/auth/oauth/providers", "POST /admin/auth/oauth/exchange",
            "POST /blog/auth/register", "POST /blog/auth/login", "POST /blog/auth/logout",
            "POST /blog/auth/email-code", "POST /blog/auth/email-login", "POST /admin/auth/email-code",
            "GET /blog/auth/oauth/providers", "POST /blog/auth/oauth/exchange",
            "GET /blog/articles", "GET /blog/archives", "GET /blog/article-rankings",
            "GET /blog/categories", "GET /blog/tags", "GET /blog/links", "GET /blog/configs",
            "GET /blog/profile", "GET /blog/social-links", "GET /blog/music", "GET /blog/messages",
            "GET /blog/statistics", "GET /blog/authors", "GET /blog/rss.xml", "GET /blog/sitemap.xml",
            "POST /blog/rss-subscriptions/unsubscribe", "POST /blog/analytics/page-views",
            "POST /blog/analytics/online/tickets",
            "GET /home/profile", "GET /home/social-links", "GET /home/configs",
            "POST /home/analytics/page-views", "POST /home/analytics/online/tickets",
            "GET /cv/profile", "GET /cv/social-links", "GET /cv/configs", "GET /cv/experiences", "GET /cv/skills",
            "POST /cv/analytics/page-views", "POST /cv/analytics/online/tickets");
    private final IUserService users;
    private final SessionManager sessions;
    private final OAuthProviderClient providers;
    private final AuthProperties properties;
    private final ObjectProvider<HttpServletRequest> requests;

    /**
     * 注册权限拦截器，先检查登录和后台入口，再检查操作权限。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先查当前身份和后台入口；SaInterceptor 默认先查注解，这里拆开才能保证报错顺序。
        registry.addInterceptor(new SaInterceptor(handler -> guard(requests.getObject(), handler))
                .isAnnotation(false)).addPathPatterns("/**");

        // 2. 身份有效后再检查业务权限，避免把账号禁用或会话失效报成缺权限。
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");

        // 3. 后台方法还得声明具体业务权限，只有 admin:access 不能操作业务。
        registry.addInterceptor(new HandlerInterceptor() {
            /**
             * 检查后台接口是否声明操作权限。
             */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler) {
                if (isAdminBusinessPath(request.getServletPath()) && handler instanceof HandlerMethod method
                        && !AnnotatedElementUtils.hasAnnotation(method.getMethod(), SaCheckPermission.class)
                        && !AnnotatedElementUtils.hasAnnotation(method.getBeanType(), SaCheckPermission.class)) {
                    throw new BusinessException(403, "FORBIDDEN", "后台入口未声明业务权限");
                }
                return true;
            }
        }).addPathPatterns("/admin/**");
    }

    /**
     * 检查请求权限，按方法和完整路径放行公开接口，其余请求检查登录和后台权限。
     */
    private void guard(HttpServletRequest request, Object handler) {
        // 1. 先放行错误分派和预检，跨域能不能通过由前面的 CORS 过滤器检查。
        if (request.getDispatcherType() == DispatcherType.ERROR || "OPTIONS".equals(request.getMethod())) {
            return;
        }

        // 2. 按请求方法和完整路径逐条放行，不能把整个 auth 或 blog 都公开。
        String path = request.getServletPath();
        String route = request.getMethod() + " " + path;
        if ("GET".equals(request.getMethod()) && path.matches("/(home|blog|cv)/ws/online")) {
            return;
        }
        if (PUBLIC.contains(route) || ("GET".equals(request.getMethod())
                && (path.matches("/blog/articles/[^/]+") || path.matches("/blog/articles/[^/]+/comments")
                || path.matches("/blog/articles/[^/]+/files/[^/]+/content")
                || path.matches("/blog/music/files/[^/]+/content")
                || path.matches("/blog/(authors|categories|tags)/[^/]+")))) {
            if (("GET /blog/messages".equals(route) || path.matches("/(home|blog|cv)/analytics/(page-views|online/tickets)")
                    && "POST".equals(request.getMethod())) && request.getHeader(
                    "Authorization") != null) {
                users.requireCurrent();
            }
            return;
        }

        // 3. OAuth 动态路径也要检查平台名单，绑定需要的原会话由业务服务检查。
        if (("POST".equals(request.getMethod()) && path.matches("/(admin|blog)/auth/oauth/[^/]+/authorize"))
                || ("GET".equals(request.getMethod()) && path.matches("/(admin|blog)/auth/oauth/[^/]+/callback"))) {
            providers.requireEnabled(path.split("/")[4]);
            return;
        }

        // 4. 其余请求都实时检查登录和账号状态，后台再检查 admin:access。
        users.requireCurrent();
        if (path.startsWith("/admin/")) {
            sessions.requireAdminAuthentication();
        }
        if (isAdminBusinessPath(path)) {
            StpUtil.checkPermission("admin:access");
        }
    }

    /** 判断是否为后台业务接口，登录和本人设置单独检查。 */
    private boolean isAdminBusinessPath(String path) {
        return path.startsWith("/admin/") && !path.startsWith("/admin/auth/")
                && !path.equals("/admin/security/not-found")
                && !path.equals("/admin/me") && !path.startsWith("/admin/me/");
    }

    /**
     * 配置跨域，只允许已配置的前端来源，开放版本等响应头，拒绝时返回统一错误。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(JsonMapper json) {
        // 1. 启动时检查每个 origin，不能用通配符，也不能带凭据或路径。
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : properties.getAllowedOrigins()) {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || StrUtil.isNotEmpty(uri.getPath())) {
                throw new IllegalArgumentException("allowed-origins必须是明确的HTTP(S) origin");
            }
        }

        // 2. 只开放约定的方法和请求头，让前端能读到版本、跳转地址和等待秒数。
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Revision",
                "X-Captcha-Id", "X-Captcha-Code", "Range"));
        config.setExposedHeaders(List.of("X-Revision", "Location", "Retry-After",
                "Accept-Ranges", "Content-Range", "Content-Length"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);

        // 3. 全部路径共用 CORS，跨域被拒绝时也返回统一 JSON 错误。
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        CorsFilter cors = new CorsFilter(source);
        cors.setCorsProcessor(new DefaultCorsProcessor() {
            /**
             * 返回跨域请求被拒绝的 JSON 结果。
             */
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

        // 4. CORS 放在鉴权和请求体处理前面，合法预检就不用带本站 Token。
        FilterRegistrationBean<CorsFilter> filter = new FilterRegistrationBean<>(cors);
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return filter;
    }
}
