package com.xuan.ratelimit;

import com.xuan.properties.AuthProperties;
import com.xuan.utils.IpUtils;
import com.xuan.utils.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 登录入口同时按 IP 和身份限流，客户端 IP 只从明确配置的直连代理读取。
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {

    private final RedisRateLimiter limiter;
    private final AuthProperties properties;

    /**
     * 登录校验前检查 IP 与用户名的失败次数，此处不增加计数。
     */
    public void checkLogin(HttpServletRequest request, String username) {
        limiter.check(keys("login", request, username.toLowerCase(Locale.ROOT)),
                properties.getRate().getLoginFailures());
    }

    /**
     * 凭据校验失败后，累计 IP 与用户名的登录失败次数。
     */
    public void loginFailed(HttpServletRequest request, String username) {
        hit("login", request, username.toLowerCase(Locale.ROOT), properties.getRate().getLoginFailures());
    }

    /**
     * 注册请求按 IP 和浏览器标识累计限流次数。
     */
    public void register(HttpServletRequest request, String browser) {
        hit("register", request, browser, properties.getRate().getRegister());
    }

    /**
     * OAuth 发起及兑换按 IP 和浏览器标识累计限流次数。
     */
    public void oauth(HttpServletRequest request, String browser) {
        hit("oauth", request, browser, properties.getRate().getOauth());
    }

    /**
     * 使用配置的窗口，同时累计 IP 与身份两个维度的次数。
     */
    private void hit(String operation, HttpServletRequest request, String identity, int limit) {
        limiter.hit(keys(operation, request, identity), properties.getRate().getWindowSeconds(), limit);
    }

    /**
     * 只从可信代理读取客户端 IP，身份先摘要再放入 Redis 键。
     */
    private List<String> keys(String operation, HttpServletRequest request, String identity) {
        // 1. 默认取直连地址，只有直连方是可信代理时才读取转发的客户端 IP。
        String ip = String.valueOf(IpUtils.clientIp(request, properties.getTrustedProxies()));

        // 2. 分别准备 IP 和身份的 Redis 键；没有身份就只限 IP，原值先做摘要再放进键。
        String prefix = properties.getRedisPrefix() + "rate:" + operation + ":";
        String dimension = identity == null ? ip : identity;
        return List.of(prefix + "ip:" + TokenUtils.digest(ip),
                prefix + "identity:" + TokenUtils.digest("login".equals(operation) ? ip + ":" + dimension : dimension));
    }

}
