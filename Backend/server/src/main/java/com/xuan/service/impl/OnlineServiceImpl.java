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
import com.xuan.utils.SitePathUtils;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.OnlineTicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

/** 在线入口只核验和签票，不写访客表、PV 或文章阅读。 */
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

    /** {@inheritDoc} */
    @Override
    public OnlineTicketVO issue(OnlineTicketCreateDTO request, String origin, String ip) {
        if (origin == null || !auth.getAllowedOrigins().contains(origin)) {
            throw new BusinessException(403, "FORBIDDEN", "在线统计来源不被允许");
        }
        if (request.pagePath().contains("?") || request.pagePath().contains("#")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "在线路径不能包含查询参数或片段");
        }
        sites.requireSite(request.siteCode(), origin, SitePathUtils.path(request.pagePath()));
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
        OnlineBinding binding = new OnlineBinding(request.siteCode(), origin, request.fingerprint(),
                userId, tokenHash, expiresAt);
        String prefix = auth.getRedisPrefix() + "rate:online:ticket:";
        limiter.hit(List.of(prefix + "ip:" + TokenUtils.digest(ip),
                prefix + "browser:" + TokenUtils.digest(binding.browserKey())), 60, List.of(120, 30));
        return new OnlineTicketVO(tickets.issue(json.writeValueAsString(binding)), 30);
    }
}
