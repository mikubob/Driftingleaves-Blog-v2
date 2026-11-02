package com.xuan.controller.blog;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.result.Result;
import com.xuan.service.IAnalyticsService;
import com.xuan.utils.IpUtils;
import com.xuan.vo.TrackingResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开访问统计接口，只返回是否记录，不创建登录会话。
 */
@RestController("blogAnalyticsController")
@RequestMapping("/blog/analytics/page-views")
@RequiredArgsConstructor
public class AnalyticsController {

    private final IAnalyticsService analytics;
    private final AuthProperties auth;

    /** 记录站点访问，检查 Origin 是否匹配站点配置，按可信代理规则获取 IP。 */
    @PostMapping
    public Result<TrackingResultVO> track(@Valid @RequestBody PageViewCreateDTO body, HttpServletRequest request) {
        if (!"blog".equals(body.getSiteCode())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "站点必须与接口所属端一致");
        }
        return Result.ok(
                analytics.track(body, request.getHeader("Origin"), IpUtils.clientIp(request, auth.getTrustedProxies()),
                        request.getHeader("User-Agent")));
    }
}
