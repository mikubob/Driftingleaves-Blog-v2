package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IStatisticsService;
import com.xuan.vo.PublicStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开汇总只包含PUBLIC内容和blog访问统计。
 */
@RestController("blogStatisticsController")
@RequestMapping("/blog/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final IStatisticsService statistics;

    /** 不提供任意日期、用户或后台内容探索。 */
    @GetMapping
    public Result<PublicStatisticsVO> statistics() {
        return Result.ok(statistics.publicStatistics());
    }
}
