package com.xuan.aspect;

import com.xuan.annotation.RateLimit;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.RedisRateLimiter;
import com.xuan.session.SessionManager;
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

/** MVC 鉴权后执行注解限流；不重复拦截已有认证限流入口。 */
@Aspect
@Component
@Order(50)
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RedisRateLimiter limiter;
    private final AuthProperties properties;
    private final SessionManager sessions;

    /** 根据稳定操作名、可信 IP 和服务端会话生成摘要键，超限前不执行目标方法。 */
    @Around("@annotation(rule)")
    public Object around(ProceedingJoinPoint point, RateLimit rule) throws Throwable {
        if (!rule.value().matches("[a-zA-Z0-9:._-]{1,100}") || rule.limit() < 1 || rule.windowSeconds() < 1) {
            throw new IllegalStateException("限流注解配置无效");
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String prefix = properties.getRedisPrefix() + "rate:interface:" + rule.value() + ":";
        List<String> keys = new ArrayList<>();
        keys.add(prefix + "ip:" + TokenUtils.digest(String.valueOf(IpUtils.clientIp(request, properties.getTrustedProxies()))));
        if (rule.dimension() != RateLimit.Dimension.IP) {
            long userId = sessions.currentUserId();
            String identity = rule.dimension() == RateLimit.Dimension.USER ? Long.toString(userId) : sessions.currentToken();
            keys.add(prefix + rule.dimension().name() + ":" + TokenUtils.digest(identity));
        }
        limiter.hit(keys, rule.windowSeconds(), rule.limit());
        return point.proceed();
    }
}
