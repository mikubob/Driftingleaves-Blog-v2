package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IStatisticsService;
import com.xuan.vo.PublicStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客公开统计接口，只包含公开内容和 blog 访问数据。
 */
@RestController("blogStatisticsController")
@RequestMapping("/blog/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final IStatisticsService statistics;

    /** 查询博客公开统计，不支持指定日期、用户或后台内容。 */
    @GetMapping
    public Result<PublicStatisticsVO> statistics() {
        return Result.ok(statistics.publicStatistics());
    }
}
