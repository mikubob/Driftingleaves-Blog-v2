package com.xuan.mapper;

import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.PublicStatisticsVO;
import com.xuan.vo.RefererMetricVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计报表数据访问接口
 */
@Mapper
public interface StatisticsMapper {

    /**
     * 查询博客端公开统计汇总
     *
     * @param today 当日 UTC 起始时间
     * @param tomorrow 次日 UTC 起始时间
     * @param now 当前 UTC 时间
     * @return 公开统计数据
     */
    PublicStatisticsVO publicStatistics(@Param("today") LocalDateTime today,
            @Param("tomorrow") LocalDateTime tomorrow,
            @Param("now") LocalDateTime now);

    /**
     * 查询管理端统计总览，时间范围仅作用于访问指标。
     *
     * @param begin 开始时间（UTC）
     * @param end 结束时间（UTC）
     * @param site 站点编码，可为空
     * @param now 当前 UTC 时间
     * @return 管理端统计总览
     */
    AdminStatisticsVO overview(@Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("site") String site,
            @Param("now") LocalDateTime now);

    /**
     * 按日期统计每日浏览量，缺失日期由业务层补零。
     *
     * @param begin 开始时间（UTC）
     * @param end 结束时间（UTC）
     * @param site 站点编码，可为空
     * @return 每日浏览量列表
     */
    List<ViewTrendVO> viewTrend(@Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("site") String site);

    /**
     * 按首次访问日期统计每日新增访客趋势
     *
     * @param begin 开始时间（UTC）
     * @param end 结束时间（UTC）
     * @param site 站点编码，可为空
     * @return 访客趋势列表
     */
    List<VisitorTrendVO> visitorTrend(@Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("site") String site);

    /**
     * 按访客当前省份统计去重后的访客分布
     *
     * @param begin 开始时间（UTC）
     * @param end 结束时间（UTC）
     * @param site 站点编码，可为空
     * @return 省份分布列表
     */
    List<ProvinceMetricVO> provinces(@Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("site") String site);

    /**
     * 统计指定时间段内的文章访问排行（仅统计 blog 站点且排除回收站文章）
     *
     * @param begin 开始时间（UTC）
     * @param end 结束时间（UTC）
     * @param limit 返回条数上限
     * @return 文章访问排行列表
     */
    List<ArticleViewMetricVO> articles(@Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit);

    /**
     * 按来源域名统计指定时间段内的访问排行
     *
     * @param begin 开始时间（UTC）
     * @param end 结束时间（UTC）
     * @param site 站点编码，可为空
     * @param limit 返回条数上限
     * @return 来源访问排行列表
     */
    List<RefererMetricVO> referers(@Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("site") String site,
            @Param("limit") int limit);
}
