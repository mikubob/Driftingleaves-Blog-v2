package com.xuan.service.impl;

import com.xuan.constant.ErrorCode;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.ratelimit.NotFoundRateLimiter;
import com.xuan.security.WebSession;
import com.xuan.service.INotFoundService;
import com.xuan.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 前端 404 上报服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotFoundServiceImpl implements INotFoundService {

    private final NotFoundRateLimiter notFoundRateLimiter;
    private final AuthProperties authProperties;
    private final PublicSiteProperties publicSiteProperties;

    /**
     * 上报前端 404 访问记录，先核验请求来源和空请求约束，再记录可信客户端 IP。
     *
     * @param request 当前 HTTP 请求
     * @throws IOException 读取请求流失败时抛出
     */
    @Override
    public void report(HttpServletRequest request) throws IOException {
        WebSession side = WebSession.forPath(request.getServletPath());
        String expectedOrigin = publicSiteProperties.origin(side.site());
        if (!expectedOrigin.equals(request.getHeader("Origin"))) {
            throw new BusinessException(403, ErrorCode.FORBIDDEN, "访问来源不被允许");
        }
        if (request.getQueryString() != null || request.getInputStream().read() != -1) {
            throw new BusinessException(400, ErrorCode.VALIDATION_ERROR, "本接口不接受参数或请求体");
        }

        String clientIp = IpUtils.clientIp(request, authProperties.getTrustedProxies());
        notFoundRateLimiter.record(clientIp);
    }
}
