package com.xuan.mapper;

import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.PublicStatisticsVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计查询接口，按保留明细汇总，UTC 起止时间由业务层传入，内容数量按当前数据统计。
 */
public interface StatisticsMapper {

    /** PUBLIC内容及blog站点汇总，不返回访客原始字段。 */
    PublicStatisticsVO publicStatistics(@Param("today") LocalDateTime today, @Param("tomorrow") LocalDateTime tomorrow,
            @Param("now") LocalDateTime now);

    /** 日期只限制访问指标，文章/评论/留言取当前状态。 */
    AdminStatisticsVO overview(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site, @Param("now") LocalDateTime now);

    /** 按日期统计浏览量，有数据的日期由 SQL 汇总，没有数据的日期由 Service 补零。 */
    List<ViewTrendVO> viewTrend(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site);

    /** 首访按visitor.first_visit_time，site筛选要求区间内有本站访问。 */
    List<VisitorTrendVO> visitorTrend(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site);

    /** 访客先去重再按当前province分组，不是访问当时的地域记录。 */
    List<ProvinceMetricVO> provinces(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site);

    /** 只统计blog明细，排除回收站，允许后台查看当前草稿/私密历史访问。 */
    List<ArticleViewMetricVO> articles(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("limit") int limit);
}
