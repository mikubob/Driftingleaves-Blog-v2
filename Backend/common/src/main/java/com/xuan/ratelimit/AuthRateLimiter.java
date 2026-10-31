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

    /** 采集在任何行锁、地域解析和写入前原子累计全部独立维度。 */
    public void analytics(String ip, String fingerprint, Long actor) {
        String prefix = properties.getRedisPrefix() + "rate:analytics:";
        java.util.List<String> keys = new java.util.ArrayList<>();
        java.util.List<Integer> limits = new java.util.ArrayList<>();
        keys.add(prefix + "ip:" + TokenUtils.digest(String.valueOf(ip))); limits.add(60);
        keys.add(prefix + "fingerprint:" + TokenUtils.digest(fingerprint)); limits.add(30);
        if (actor != null) { keys.add(prefix + "user:" + TokenUtils.digest(actor.toString())); limits.add(60); }
        limiter.hit(keys, 60, limits);
    }

    /** 五类后台报表共用可信 IP 与本站用户预算。 */
    public void report(HttpServletRequest request, long actor) {
        limiter.hit(keys("report", request, Long.toString(actor)), 60, 30);
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
