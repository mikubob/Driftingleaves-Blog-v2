package com.xuan.aspect;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.ArticleQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.dto.MessagePublicQueryDTO;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.RedisRateLimiter;
import com.xuan.session.SessionManager;
import com.xuan.utils.IdUtils;
import com.xuan.utils.IpUtils;
import com.xuan.utils.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 按注解限制请求频率，在 MVC 权限检查后执行，已有认证限流的接口不重复计数。
 */
@Aspect
@Component
@Order(50)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisRateLimiter limiter;
    private final AuthProperties properties;
    private final SessionManager sessions;

    /** 检查公开 GET 请求频率，在参数转换后、读取缓存或修改数据前执行。 */
    @Around("execution(public * com.xuan.service.impl.*.public*(..)) || " +
            "execution(* com.xuan.service.impl.ArticleServiceImpl.archives(..)) || " +
            "execution(* com.xuan.service.impl.ArticleServiceImpl.rankings(..)) || " +
            "execution(* com.xuan.service.impl.FeedServiceImpl.rss(..)) || " +
            "execution(* com.xuan.service.impl.FeedServiceImpl.sitemap(..)) || " +
            "execution(* com.xuan.service.impl.FileServiceImpl.attachment(..))")
    public Object publicReads(ProceedingJoinPoint point) throws Throwable {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return point.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        if (!"GET".equals(request.getMethod())) {
            return point.proceed();
        }
        // 查询id保留字符串，计入限流次数前先检查格式，拒绝前导零；Service 使用同一规则转换类型。
        for (Object argument : point.getArgs()) {
            if (argument instanceof ArticleQueryDTO query) {
                IdUtils.parse(query.categoryId());
                IdUtils.parse(query.tagId());
                IdUtils.parse(query.authorId());
            } else if (argument instanceof CommentPublicQueryDTO query) {
                IdUtils.parse(query.parentId());
            } else if (argument instanceof MessagePublicQueryDTO query) {
                IdUtils.parse(query.parentId());
            }
        }
        String path = request.getServletPath();
        if (path.matches("/blog/articles/[^/]+/files/[^/]+/content")) {
            hitIp("public-file", 120, request);
        } else if (path.startsWith("/blog/") || path.startsWith("/home/") || path.startsWith("/cv/")) {
            hitIp("public-get", 120, request);
            if ("/blog/articles".equals(path) && request.getParameter("keyword") != null) {
                hitIp("public-search", 10, request);
            }
            if (path.matches("/blog/articles/[^/]+")) {
                hitIp("public-detail", 60, request);
            }
            if (path.equals("/blog/rss.xml") || path.equals("/blog/sitemap.xml")) {
                hitIp("public-xml", 30, request);
            }
        }
        return point.proceed();
    }

    /** 按操作和可信客户端 IP 统计一分钟内的请求次数。 */
    private void hitIp(String operation, int limit, HttpServletRequest request) {
        String clientIp = String.valueOf(IpUtils.clientIp(request, properties.getTrustedProxies()));
        String key = properties.getRedisPrefix() + "rate:interface:" + operation + ":ip:" + TokenUtils.digest(clientIp);
        limiter.hit(List.of(key), 60, limit);
    }

    /** 检查请求频率，按操作名、可信 IP 和登录会话计数，超限时不执行方法。 */
    @Around("@annotation(rule)")
    public Object around(ProceedingJoinPoint point, RateLimit rule) throws Throwable {
        if (!rule.value().matches("[a-zA-Z0-9:._-]{1,100}") || rule.limit() < 1 || rule.windowSeconds() < 1) {
            throw new IllegalStateException("限流注解配置无效");
        }
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return point.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String prefix = properties.getRedisPrefix() + "rate:interface:" + rule.value() + ":";
        List<String> keys = new ArrayList<>();
        keys.add(prefix + "ip:" + TokenUtils.digest(
                String.valueOf(IpUtils.clientIp(request, properties.getTrustedProxies()))));
        if (rule.dimension() != RateLimit.Dimension.IP) {
            long userId = sessions.currentUserId();
            String identity = rule.dimension() == RateLimit.Dimension.USER ? Long.toString(
                    userId) : sessions.currentToken();
            keys.add(prefix + rule.dimension().name() + ":" + TokenUtils.digest(identity));
        }
        limiter.hit(keys, rule.windowSeconds(), rule.limit());
        return point.proceed();
    }
}
