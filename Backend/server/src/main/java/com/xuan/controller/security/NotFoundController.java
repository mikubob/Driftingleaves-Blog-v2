package com.xuan.controller.security;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.ratelimit.NotFoundRateLimiter;
import com.xuan.result.Result;
import com.xuan.security.WebSession;
import com.xuan.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** 前端 404 上报接口，只记录当前 IP 的本次访问，不接受目标身份或完整 URL。 */
@RestController
@RequiredArgsConstructor
public class NotFoundController {

    private final NotFoundRateLimiter guard;
    private final AuthProperties properties;
    private final PublicSiteProperties sites;

    /** 上报前端 404，先检查来源和请求参数，再与后端 404 共用计数窗口。 */
    @PostMapping({"/blog/security/not-found", "/admin/security/not-found"})
    public Result<Void> report(HttpServletRequest request) throws IOException {
        WebSession side = WebSession.forPath(request.getServletPath());
        if (!sites.origin(side.site()).equals(request.getHeader("Origin"))) {
            throw new BusinessException(403, "FORBIDDEN", "访问来源不被允许");
        }
        if (request.getQueryString() != null || request.getInputStream().read() != -1) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受参数或请求体");
        }
        guard.record(IpUtils.clientIp(request, properties.getTrustedProxies()));
        return Result.ok(null);
    }
}
