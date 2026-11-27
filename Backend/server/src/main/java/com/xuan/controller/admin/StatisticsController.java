package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.StatisticsQueryDTO;
import com.xuan.result.Result;
import com.xuan.service.IStatisticsService;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.RefererMetricVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端统计报表接口
 */
@Slf4j
@RestController("adminStatisticsController")
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@SaCheckPermission("blog:statistics:query")
public class StatisticsController {

    private final IStatisticsService statisticsService;

    /**
     * 查询统计总览
     *
     * @param query 日期查询参数
     * @return 统计总览数据
     */
    @GetMapping("/overview")
    public Result<AdminStatisticsVO> overview(@Valid @ModelAttribute StatisticsQueryDTO query) {
        log.info("查询后台统计总览: {}", query);
        AdminStatisticsVO overview = statisticsService.overview(query);
        return Result.ok(overview);
    }

    /**
     * 查询每日浏览量趋势
     *
     * @param query 日期查询参数
     * @return 每日浏览量列表
     */
    @GetMapping("/page-views")
    public Result<List<ViewTrendVO>> views(@Valid @ModelAttribute StatisticsQueryDTO query) {
        log.info("查询每日浏览量趋势: {}", query);
        List<ViewTrendVO> trend = statisticsService.viewTrend(query);
        return Result.ok(trend);
    }

    /**
     * 查询每日新增及累计访客趋势
     *
     * @param query 日期查询参数
     * @return 访客趋势列表
     */
    @GetMapping("/visitors")
    public Result<List<VisitorTrendVO>> visitors(@Valid @ModelAttribute StatisticsQueryDTO query) {
        log.info("查询访客趋势: {}", query);
        List<VisitorTrendVO> trend = statisticsService.visitorTrend(query);
        return Result.ok(trend);
    }

    /**
     * 查询访客省份分布
     *
     * @param query 日期查询参数
     * @return 省份分布列表
     */
    @GetMapping("/provinces")
    public Result<List<ProvinceMetricVO>> provinces(@Valid @ModelAttribute StatisticsQueryDTO query) {
        log.info("查询访客省份分布: {}", query);
        List<ProvinceMetricVO> provinces = statisticsService.provinces(query);
        return Result.ok(provinces);
    }

    /**
     * 查询文章访问排行
     *
     * @param query 日期查询参数
     * @return 文章访问排行列表
     */
    @GetMapping("/articles")
    public Result<List<ArticleViewMetricVO>> articles(@Valid @ModelAttribute StatisticsQueryDTO query) {
        log.info("查询文章访问排行: {}", query);
        List<ArticleViewMetricVO> articles = statisticsService.articles(query);
        return Result.ok(articles);
    }

    /**
     * 查询访问来源域名排行
     *
     * @param query 日期、站点及条数查询参数
     * @return 访问来源排行列表
     */
    @GetMapping("/referers")
    public Result<List<RefererMetricVO>> referers(@Valid @ModelAttribute StatisticsQueryDTO query) {
        log.info("查询访问来源排行: {}", query);
        List<RefererMetricVO> referers = statisticsService.referers(query);
        return Result.ok(referers);
    }
}
