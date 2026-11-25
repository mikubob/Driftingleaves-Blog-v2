package com.xuan.controller.cv;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.result.Result;
import com.xuan.service.IAnalyticsService;
import com.xuan.vo.TrackingResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历端访问统计接口
 */
@Slf4j
@RestController("cvAnalyticsController")
@RequestMapping("/cv/analytics/page-views")
@RequiredArgsConstructor
public class AnalyticsController {

    private final IAnalyticsService analyticsService;

    /**
     * 记录简历端页面访问
     *
     * @param body 访问记录参数
     * @param request 当前 HTTP 请求
     * @return 采集结果
     */
    @PostMapping
    public Result<TrackingResultVO> track(@Valid @RequestBody PageViewCreateDTO body,
            HttpServletRequest request) {
        log.info("记录简历端页面访问: pagePath={}", body.getPagePath());
        String origin = request.getHeader("Origin");
        TrackingResultVO result = analyticsService.trackSite("cv", body, origin, request);
        return Result.ok(result);
    }
}
