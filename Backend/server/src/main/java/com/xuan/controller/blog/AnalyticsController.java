package com.xuan.controller.blog;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.result.Result;
import com.xuan.security.WebSession;
import com.xuan.service.IAnalyticsService;
import com.xuan.vo.TrackingResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端访问统计接口
 */
@Slf4j
@RestController("blogAnalyticsController")
@RequestMapping("/blog/analytics/page-views")
@RequiredArgsConstructor
public class AnalyticsController {

    private final IAnalyticsService analyticsService;

    /**
     * 记录博客端页面访问
     *
     * @param body 访问记录参数
     * @param origin 已校验的请求来源
     * @param request 当前 HTTP 请求
     * @return 采集结果
     */
    @PostMapping
    public Result<TrackingResultVO> track(@Valid @RequestBody PageViewCreateDTO body,
            @RequestAttribute(WebSession.VERIFIED_ORIGIN) String origin,
            HttpServletRequest request) {
        log.info("记录博客端页面访问: pagePath={}", body.getPagePath());
        TrackingResultVO result = analyticsService.trackSite("blog", body, origin, request);
        return Result.ok(result);
    }
}
