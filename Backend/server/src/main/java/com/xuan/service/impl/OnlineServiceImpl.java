package com.xuan.service.impl;

import com.xuan.dto.OnlineTicketCreateDTO;
import com.xuan.exception.BusinessException;
import com.xuan.model.OnlineBinding;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.ratelimit.RedisRateLimiter;
import com.xuan.service.IOnlineService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.OnlineTicketStore;
import com.xuan.utils.IpUtils;
import com.xuan.utils.SitePathUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.OnlineTicketVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

/**
 * 在线入口只核验和签票，不写访客表、PV 或文章阅读。
 */
@Service
@RequiredArgsConstructor
public class OnlineServiceImpl implements IOnlineService {
    private final PublicSiteProperties sites;
    private final AuthProperties auth;
    private final RedisRateLimiter limiter;
    private final SessionManager sessions;
    private final IUserService users;
    private final OnlineTicketStore tickets;
    private final JsonMapper json;
    private final Clock clock;

    /**
     * 校验站点归属并解析客户端 IP，再生成一次性在线统计票据。
     *
     * @param expectedSiteCode 接口所属端站点编码
     * @param request 票据创建参数
     * @param origin 请求来源
     * @param httpRequest 当前 HTTP 请求
     * @return 在线统计票据
     */
    @Override
    public OnlineTicketVO issueForSite(String expectedSiteCode, OnlineTicketCreateDTO request, String origin,
            HttpServletRequest httpRequest) {
        if (!expectedSiteCode.equals(request.siteCode())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "站点必须与接口所属端一致");
        }
        String clientIp = IpUtils.clientIp(httpRequest, auth.getTrustedProxies());
        return issue(request, origin, clientIp);
    }

    /**
     * 生成一次性在线统计票据。
     */
    @Override
    public OnlineTicketVO issue(OnlineTicketCreateDTO request, String origin, String ip) {
        // 1. 校验页面路径与所属站点来源是否匹配。
        if (request.pagePath().contains("?") || request.pagePath().contains("#")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "在线路径不能包含查询参数或片段");
        }
        sites.requireSite(request.siteCode(), origin, SitePathUtils.path(request.pagePath()));

        // 2. 已登录时校验当前会话并记录 Token 摘要与剩余有效期。
        String token = sessions.currentToken();
        Long userId = null;
        String tokenHash = null;
        long expiresAt = 0;
        if (token != null) {
            userId = users.requireCurrent();
            sessions.requireActive(token, userId);
            long remaining = sessions.remainingSeconds(token);
            if (remaining <= 0) {
                throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
            }
            tokenHash = TokenUtils.digest(token);
            expiresAt = clock.millis() + remaining * 1000;
        }

        // 3. 按 IP 与浏览器维度限流后签发 30 秒有效的一次性票据。
        OnlineBinding binding = new OnlineBinding(request.siteCode(), origin, request.fingerprint(),
                userId, tokenHash, expiresAt);
        String prefix = auth.getRedisPrefix() + "rate:online:ticket:";
        limiter.hit(List.of(prefix + "ip:" + TokenUtils.digest(ip),
                prefix + "browser:" + TokenUtils.digest(binding.browserKey())), 60, List.of(120, 30));
        return new OnlineTicketVO(tickets.issue(json.writeValueAsString(binding)), 30);
    }
}
