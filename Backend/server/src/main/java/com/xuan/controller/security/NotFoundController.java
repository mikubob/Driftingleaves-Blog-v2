package com.xuan.controller.security;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.NotFoundGuard;
import com.xuan.result.Result;
import com.xuan.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** SPA 兜底路由只上报一次当前 IP 的不存在访问，不接受目标身份或完整 URL。 */
@RestController
@RequiredArgsConstructor
public class NotFoundController {

    private final NotFoundGuard guard;
    private final AuthProperties properties;

    /** 来源和无参数约定通过后，和后端真实 404 共用同一个窗口。 */
    @PostMapping({"/blog/security/not-found", "/admin/security/not-found"})
    public Result<Void> report(HttpServletRequest request) throws IOException {
        if (!properties.getAllowedOrigins().contains(request.getHeader("Origin"))) {
            throw new BusinessException(403, "FORBIDDEN", "访问来源不被允许");
        }
        if (request.getQueryString() != null || request.getInputStream().read() != -1) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受参数或请求体");
        }
        guard.record(IpUtils.clientIp(request, properties.getTrustedProxies()));
        return Result.ok(null);
    }
}
