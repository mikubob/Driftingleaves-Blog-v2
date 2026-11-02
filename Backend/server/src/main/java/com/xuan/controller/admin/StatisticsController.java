package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.StatisticsQueryDTO;
import com.xuan.result.Result;
import com.xuan.service.IStatisticsService;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台统计报表接口，要求查询权限的 ALL 范围，日期使用 UTC 自然日。
 */
@RestController("adminStatisticsController")
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@SaCheckPermission("blog:statistics:query")
public class StatisticsController {

    private final IStatisticsService statistics;

    /** 查询统计总览，日期只限制访问指标，内容和待审核数量按当前数据统计。 */
    @GetMapping("/overview")
    public Result<AdminStatisticsVO> overview(@Valid @ModelAttribute StatisticsQueryDTO query) {
        return Result.ok(statistics.overview(query));
    }

    /** 查询每日浏览量，没有数据的日期补零。 */
    @GetMapping("/page-views")
    public Result<List<ViewTrendVO>> views(@Valid @ModelAttribute StatisticsQueryDTO query) {
        return Result.ok(statistics.viewTrend(query));
    }

    /** 查询每日新增及累计访客数，按首次访问日期统计，从当前时间段的 begin 开始累计。 */
    @GetMapping("/visitors")
    public Result<List<VisitorTrendVO>> visitors(@Valid @ModelAttribute StatisticsQueryDTO query) {
        return Result.ok(statistics.visitorTrend(query));
    }

    /** 查询访客省份分布，按当前省份统计，同一访客只算一次。 */
    @GetMapping("/provinces")
    public Result<List<ProvinceMetricVO>> provinces(@Valid @ModelAttribute StatisticsQueryDTO query) {
        return Result.ok(statistics.provinces(query));
    }

    /** 查询文章访问排行，按指定时间段内的 blog 浏览量排名，不使用文章累计热度。 */
    @GetMapping("/articles")
    public Result<List<ArticleViewMetricVO>> articles(@Valid @ModelAttribute StatisticsQueryDTO query) {
        return Result.ok(statistics.articles(query));
    }
}
