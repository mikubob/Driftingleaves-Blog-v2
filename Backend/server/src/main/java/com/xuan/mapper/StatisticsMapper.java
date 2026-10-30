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
 * 报表按保留明细统计，UTC边界由业务传入，内容计数是当前快照。
 */
public interface StatisticsMapper {

    /** PUBLIC内容及blog站点汇总，不返回访客原始字段。 */
    PublicStatisticsVO publicStatistics(@Param("today") LocalDateTime today, @Param("tomorrow") LocalDateTime tomorrow,
            @Param("now") LocalDateTime now);

    /** 日期只限制访问指标，文章/评论/留言取当前状态。 */
    AdminStatisticsVO overview(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site, @Param("now") LocalDateTime now);

    /** 有数据日期由SQL聚合，无数据日期由Service补零。 */
    List<ViewTrendVO> viewTrend(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site);

    /** 首访按visitor.first_visit_time，site筛选要求区间内有本站访问。 */
    List<VisitorTrendVO> visitorTrend(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site);

    /** 访客先去重再按当前province分组，不是历史地域快照。 */
    List<ProvinceMetricVO> provinces(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("site") String site);

    /** 只统计blog明细，排除回收站，允许后台查看当前草稿/私密历史访问。 */
    List<ArticleViewMetricVO> articles(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("limit") int limit);
}
