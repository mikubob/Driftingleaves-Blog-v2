package com.xuan.ratelimit;

import com.xuan.properties.AuthProperties;
import com.xuan.utils.IpUtils;
import com.xuan.utils.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 登录限流，同时检查 IP 和身份，客户端 IP 按可信代理配置获取。
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {

    private final RedisRateLimiter limiter;
    private final AuthProperties properties;

    /**
     * 检查登录失败次数，按 IP 和用户名限制，此处不增加次数。
     */
    public void checkLogin(HttpServletRequest request, String username) {
        limiter.check(keys("login", request, username.toLowerCase(Locale.ROOT)),
                properties.getRate().getLoginFailures());
    }

    /**
     * 记录登录失败，分别增加 IP 和用户名的失败次数。
     */
    public void loginFailed(HttpServletRequest request, String username) {
        hit("login", request, username.toLowerCase(Locale.ROOT), properties.getRate().getLoginFailures());
    }

    /**
     * 检查注册请求频率，按 IP 和浏览器标识计数。
     */
    public void register(HttpServletRequest request, String browser) {
        hit("register", request, browser, properties.getRate().getRegister());
    }

    /** 限制第三方登录发起次数，两端和各平台共用 IP、浏览器各自的每分钟三次额度。 */
    public void oauthLogin(HttpServletRequest request, String browser) {
        limiter.hit(keys("oauth-login", request, browser), 60, 3);
    }

    /** 检查第三方绑定发起和票据兑换频率，按 IP 和浏览器标识计数。 */
    public void oauth(HttpServletRequest request, String browser) {
        hit("oauth", request, browser, properties.getRate().getOauth());
    }

    /** 检查邮件发送频率，所有用途共用限制：同一 IP 每分钟五次，同一邮箱每分钟一次。 */
    public void email(HttpServletRequest request, String email) {
        limiter.hit(keys("email", request, email.strip().toLowerCase(Locale.ROOT)), 60, List.of(5, 1));
    }

    /** 检查访问采集频率，在加锁、查询属地和写入前，用一次脚本完成各项计数。 */
    public void analytics(String ip, String fingerprint, Long actor) {
        String prefix = properties.getRedisPrefix() + "rate:analytics:";
        List<String> keys = new ArrayList<>();
        List<Integer> limits = new ArrayList<>();
        keys.add(prefix + "ip:" + TokenUtils.digest(String.valueOf(ip)));
        limits.add(60);
        keys.add(prefix + "fingerprint:" + TokenUtils.digest(fingerprint));
        limits.add(30);
        if (actor != null) {
            keys.add(prefix + "user:" + TokenUtils.digest(actor.toString()));
            limits.add(60);
        }
        limiter.hit(keys, 60, limits);
    }

    /** 检查后台报表请求频率，五类报表共用 IP 和用户次数限制。 */
    public void report(HttpServletRequest request, long actor) {
        limiter.hit(keys("report", request, Long.toString(actor)), 60, 30);
    }

    /**
     * 按配置的时间窗口计数，同时检查 IP 和身份。
     */
    private void hit(String operation, HttpServletRequest request, String identity, int limit) {
        limiter.hit(keys(operation, request, identity), properties.getRate().getWindowSeconds(), limit);
    }

    /**
     * 生成限流缓存键，IP 按可信代理规则获取，身份先计算摘要。
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
