package com.xuan.config;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.xuan.client.OAuthProviderClient;
import com.xuan.exception.BusinessException;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.result.Result;
import com.xuan.security.WebSession;
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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
            "POST /admin/auth/email-login", "GET /blog/auth/math-captcha",
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
    private static final Set<String> ONLINE_SOCKETS = Set.of("/home/ws/online", "/blog/ws/online", "/cv/ws/online");
    private static final Set<String> ANONYMOUS_WRITES = Set.of(
            "POST /admin/security/not-found", "POST /blog/security/not-found",
            "POST /blog/rss-subscriptions/unsubscribe");
    private final IUserService users;
    private final SessionManager sessions;
    private final OAuthProviderClient providers;
    private final PublicSiteProperties sites;
    private final Environment environment;
    private final ObjectProvider<HttpServletRequest> requests;

    /** 让 Sa-Token 只使用本应用已经选好的请求会话。 */
    @Bean
    public StpLogic webStpLogic() {
        return new StpLogic(StpUtil.TYPE) {
            @Override
            public String getTokenValueNotCut() {
                Object token = SaHolder.getStorage().get(splicingKeyJustCreatedSave());
                return token instanceof String value ? value : null;
            }
        };
    }

    /**
     * 注册权限拦截器，先检查登录和后台入口，再检查操作权限。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先查当前身份和后台入口；SaInterceptor 默认先查注解，这里拆开才能保证报错顺序。
        registry.addInterceptor(new SaInterceptor(handler -> guard(requests.getObject()))
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
    private void guard(HttpServletRequest request) {
        // 1. 先放行错误分派和预检，跨域能不能通过由前面的 CORS 过滤器检查。
        if (request.getDispatcherType() == DispatcherType.ERROR || "OPTIONS".equals(request.getMethod())) {
            return;
        }

        String path = request.getServletPath();
        String method = request.getMethod();
        WebSession side = WebSession.forPath(path);
        String route = method + " " + path;
        boolean write = requiresOrigin(side, method, route);
        if (write) {
            String trustedOrigin = sites.origin(side.site());
            requireOrigin(request, trustedOrigin);
            request.setAttribute(WebSession.VERIFIED_ORIGIN, trustedOrigin);
        }

        boolean callback = path.matches("/(admin|blog)/auth/oauth/[^/]+/callback");
        if (callback && "HEAD".equals(method)) {
            throw new BusinessException(405, "METHOD_NOT_ALLOWED", "请求方法不被允许");
        }
        if ("GET".equals(method) && ONLINE_SOCKETS.contains(path)) {
            return;
        }

        // 可选登录接口只有在页面明确提供预期账号时才使用登录 Cookie。
        boolean publicRoute = isPublic(method, path);
        boolean optional = optionalSession(method, path);
        boolean logout = "POST".equals(method) && path.matches("/(admin|blog)/auth/logout");
        boolean exchange = "POST".equals(method) && path.matches("/(admin|blog)/auth/oauth/exchange");
        String expectedUserId = request.getHeader(WebSession.EXPECTED_USER);
        boolean usesSession = !publicRoute || logout || exchange || (optional && expectedUserId != null);
        if (side != null && usesSession && !callback) {
            String token = side.readToken(request);
            if (token != null) {
                StpUtil.getStpLogic().setTokenValueToStorage(token);
            }
            if (logout && token != null) {
                Object userId = StpUtil.getLoginIdByToken(token);
                if (userId != null) {
                    WebSession.requireExpectedUser(expectedUserId, userId);
                }
            }
            if (optional) {
                WebSession.requireExpectedUser(expectedUserId, users.requireCurrent());
            }
        }
        if (publicRoute) {
            return;
        }

        // 平台回调和发起必须匹配同一个已配置的端；绑定的原会话由业务层检查。
        boolean authorize = "POST".equals(method) && path.matches("/(admin|blog)/auth/oauth/[^/]+/authorize");
        if (authorize || (callback && "GET".equals(method))) {
            String expected = providers.callbackPath(path.split("/")[4]);
            String callbackPath = authorize ? path.substring(0, path.lastIndexOf('/')) + "/callback" : path;
            if (!expected.equals("/api/v1" + callbackPath)) {
                throw new BusinessException(404, "NOT_FOUND", "平台不存在或未启用");
            }
            return;
        }

        // 4. 其余请求都实时检查登录和账号状态，后台再检查 admin:access。
        long userId = users.requireCurrent();
        if (side != null && (write || expectedUserId != null)) {
            WebSession.requireExpectedUser(expectedUserId, userId);
        }
        if (side == WebSession.ADMIN) {
            sessions.requireAdminAuthentication();
        }
        if (isAdminBusinessPath(path)) {
            StpUtil.checkPermission("admin:access");
        }
    }

    /** 判断公开接口，涉及身份的讨论和统计接口仍单独处理。 */
    private static boolean isPublic(String method, String path) {
        return PUBLIC.contains(method + " " + path) || ("GET".equals(method)
                && (path.matches("/blog/articles/[^/]+") || path.matches("/blog/articles/[^/]+/comments")
                || path.matches("/blog/articles/[^/]+/files/[^/]+/content")
                || path.matches("/blog/music/files/[^/]+/content")
                || path.matches("/blog/(authors|categories|tags)/[^/]+")));
    }

    private static boolean optionalSession(String method, String path) {
        return ("GET".equals(method) && (path.equals("/blog/messages") || path.matches("/blog/articles/[^/]+/comments")))
                || ("POST".equals(method) && path.matches("/blog/analytics/(page-views|online/tickets)"));
    }

    private static boolean requiresOrigin(WebSession side, String method, String route) {
        return side != null && !Set.of("GET", "HEAD", "OPTIONS").contains(method) && !ANONYMOUS_WRITES.contains(route);
    }

    /** 优先核对 Origin，只有请求缺少 Origin 时才从 Referer 提取来源。 */
    private static void requireOrigin(HttpServletRequest request, String trustedOrigin) {
        List<String> origins = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        if (origins.equals(List.of(trustedOrigin))) {
            return;
        }
        if (origins.isEmpty()) {
            List<String> referers = Collections.list(request.getHeaders(HttpHeaders.REFERER));
            if (referers.size() == 1) {
                try {
                    URI referer = URI.create(referers.getFirst());
                    if (referer.getScheme() != null && referer.getHost() != null && referer.getRawUserInfo() == null
                            && referer.getRawFragment() == null
                            && trustedOrigin.equals(PublicSiteProperties.originOf(referer))) {
                        return;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 无法解析时拒绝，不从请求 Host 或转发头推断来源。
                }
            }
        }
        throw new BusinessException(403, "FORBIDDEN", "请求来源不被允许");
    }

    /** 判断是否为后台业务接口，登录和本人设置单独检查。 */
    private boolean isAdminBusinessPath(String path) {
        return path.startsWith("/admin/") && !path.startsWith("/admin/auth/")
                && !path.equals("/admin/security/not-found")
                && !path.equals("/admin/me") && !path.startsWith("/admin/me/");
    }

    /** 仅为主页和简历的公开客户端开放 HTTP 跨域。 */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(JsonMapper json) {
        String adminOrigin = sites.origin("admin");
        String blogOrigin = sites.origin("blog");
        validateSessionOrigins(URI.create(adminOrigin), URI.create(blogOrigin));
        Map<WebSession, String> sessionOrigins = Map.of(WebSession.ADMIN, adminOrigin, WebSession.BLOG, blogOrigin);
        CorsConfiguration homeCors = publicCors(sites.origin("home"));
        CorsConfiguration cvCors = publicCors(sites.origin("cv"));
        CorsConfiguration denied = new CorsConfiguration();
        CorsFilter cors = new CorsFilter(request -> {
            String path = request.getServletPath();
            boolean preflight = CorsUtils.isPreFlightRequest(request);
            String method = preflight ? request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) : request.getMethod();
            if (!preflight && "GET".equals(method) && ONLINE_SOCKETS.contains(path)) {
                // WebSocket 握手由各站自己的 Origin 校验处理。
                return null;
            }
            if (PUBLIC.contains(method + " " + path)) {
                if (path.startsWith("/home/")) {
                    return homeCors;
                }
                if (path.startsWith("/cv/")) {
                    return cvCors;
                }
            }

            // 同源反代可能改写后端看到的 Host。允许本端请求继续执行，不添加跨域许可。
            WebSession side = WebSession.forPath(path);
            if (!preflight && side != null && sessionOrigins.get(side).equals(request.getHeader(HttpHeaders.ORIGIN))) {
                return null;
            }
            return denied;
        });
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

        // CORS 在鉴权和请求体处理前，合法预检不需要登录 Cookie。
        FilterRegistrationBean<CorsFilter> filter = new FilterRegistrationBean<>(cors);
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return filter;
    }

    private CorsConfiguration publicCors(String origin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE));
        config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        config.setMaxAge(600L);
        return config;
    }

    private void validateSessionOrigins(URI admin, URI blog) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev & !prd"));
        boolean localAdmin = dev && isLoopback(admin);
        boolean localBlog = dev && isLoopback(blog);
        if ((!localAdmin && !"https".equals(admin.getScheme())) || (!localBlog && !"https".equals(blog.getScheme()))) {
            throw new IllegalArgumentException("管理端和博客须使用HTTPS，dev允许回环HTTP");
        }
        if (admin.getHost().equals(blog.getHost())
                && !(localAdmin && localBlog && admin.getPort() != blog.getPort())) {
            throw new IllegalArgumentException("管理端和博客须使用不同主机名，dev回环地址可使用不同端口");
        }
    }

    private boolean isLoopback(URI origin) {
        String host = origin.getHost();
        return host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("localhost") || host.endsWith(".localhost");
    }
}
