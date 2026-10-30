package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.StatisticsQueryDTO;
import com.xuan.result.Result;
import com.xuan.service.IStatisticsService;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.ArticleViewMetricVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;


/** 后台报表需要本次query权限的ALL，日期统一使用UTC自然日。 */
@RestController("adminStatisticsController")
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@SaCheckPermission("blog:statistics:query")
public class StatisticsController {
    private final IStatisticsService statistics;
    /** 日期仅限制访问指标，内容与待审核数量为当前快照。 */
    @GetMapping("/overview")
    public Result<AdminStatisticsVO> overview(@Valid @ModelAttribute StatisticsQueryDTO query) { return Result.ok(statistics.overview(query)); }
    /** 无数据日期补0。 */
    @GetMapping("/page-views")
    public Result<List<ViewTrendVO>> views(@Valid @ModelAttribute StatisticsQueryDTO query) { return Result.ok(statistics.viewTrend(query)); }
    /** 按首访日期并从当前区间begin累计。 */
    @GetMapping("/visitors")
    public Result<List<VisitorTrendVO>> visitors(@Valid @ModelAttribute StatisticsQueryDTO query) { return Result.ok(statistics.visitorTrend(query)); }
    /** 按最新省份对访客去重。 */
    @GetMapping("/provinces")
    public Result<List<ProvinceMetricVO>> provinces(@Valid @ModelAttribute StatisticsQueryDTO query) { return Result.ok(statistics.provinces(query)); }
    /** 按blog区间浏览数排名，区别于累计文章热度。 */
    @GetMapping("/articles")
    public Result<List<ArticleViewMetricVO>> articles(@Valid @ModelAttribute StatisticsQueryDTO query) { return Result.ok(statistics.articles(query)); }
}
