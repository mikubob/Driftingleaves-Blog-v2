package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IStatisticsService;
import com.xuan.vo.PublicStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端公开统计接口
 */
@Slf4j
@RestController("blogStatisticsController")
@RequestMapping("/blog/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final IStatisticsService statisticsService;

    /**
     * 查询博客公开统计数据
     *
     * @return 公开统计数据
     */
    @GetMapping
    public Result<PublicStatisticsVO> statistics() {
        log.info("博客端查询公开统计数据");
        PublicStatisticsVO statistics = statisticsService.publicStatistics();
        return Result.ok(statistics);
    }
}
