package com.xuan.ratelimit;

import com.xuan.properties.AuthProperties;
import com.xuan.utils.TokenUtils;
import com.xuan.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

/** 认证入口双维度 Redis 固定窗口限流；仅信任指定直连代理的客户端地址。 */
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {
    private final RedisRateLimiter limiter;
    private final AuthProperties properties;

    public void checkLogin(HttpServletRequest request, String username) {
        limiter.check(keys("login", request, username.toLowerCase(Locale.ROOT)), properties.getRate().getLoginFailures());
    }

    public void loginFailed(HttpServletRequest request, String username) {
        hit("login", request, username.toLowerCase(Locale.ROOT), properties.getRate().getLoginFailures());
    }

    public void register(HttpServletRequest request, String browser) {
        hit("register", request, browser, properties.getRate().getRegister());
    }

    public void oauth(HttpServletRequest request, String browser) {
        hit("oauth", request, browser, properties.getRate().getOauth());
    }

    private void hit(String operation, HttpServletRequest request, String identity, int limit) {
        limiter.hit(keys(operation, request, identity), properties.getRate().getWindowSeconds(), limit);
    }

    private List<String> keys(String operation, HttpServletRequest request, String identity) {
        // 1. 默认使用直连地址，仅在直连方属于受信代理时读取转发的客户端 IP。
        String ip = String.valueOf(IpUtils.clientIp(request, properties.getTrustedProxies()));
        // 2. 分别构建 IP 与身份维度的键，身份缺失时退为 IP，摘要避免暴露原值。
        String prefix = properties.getRedisPrefix() + "rate:" + operation + ":";
        String dimension = identity == null ? ip : identity;
        return List.of(prefix + "ip:" + TokenUtils.digest(ip),
                prefix + "identity:" + TokenUtils.digest("login".equals(operation) ? ip + ":" + dimension : dimension));
    }

}
