package com.xuan.controller.cv;

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
 * 公开采集仅返回是否记录，不创建访客会话。
 */
@RestController("cvAnalyticsController")
@RequestMapping("/cv/analytics/page-views")
@RequiredArgsConstructor
public class AnalyticsController {

    private final IAnalyticsService analytics;
    private final AuthProperties auth;

    /** Origin必须与明确站点配置匹配；IP只从既有可信代理解析器提取。 */
    @PostMapping
    public Result<TrackingResultVO> track(@Valid @RequestBody PageViewCreateDTO body, HttpServletRequest request) {
        if (!"cv".equals(body.getSiteCode())) {
            throw new BusinessException(400, "VALIDATION_ERROR", "站点必须与接口所属端一致");
        }
        return Result.ok(
                analytics.track(body, request.getHeader("Origin"), IpUtils.clientIp(request, auth.getTrustedProxies()),
                        request.getHeader("User-Agent")));
    }
}
