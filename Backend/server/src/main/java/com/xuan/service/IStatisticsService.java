package com.xuan.service;

import com.xuan.dto.StatisticsQueryDTO;
import com.xuan.vo.PublicStatisticsVO;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.ArticleViewMetricVO;
import java.util.List;

/** 公开汇总和后台日期报表分开授权，全部计数按当前保留记录派生。 */
public interface IStatisticsService {
    /** 仅PUBLIC内容及blog站点汇总。 */
    PublicStatisticsVO publicStatistics();
    /** 当前内容快照与指定UTC区间访问指标。 */
    AdminStatisticsVO overview(StatisticsQueryDTO query);
    /** 按UTC日期补零。 */
    List<ViewTrendVO> viewTrend(StatisticsQueryDTO query);
    /** 按首访日期补零，并从本区间begin累计。 */
    List<VisitorTrendVO> visitorTrend(StatisticsQueryDTO query);
    /** 当前省份分布，访客去重。 */
    List<ProvinceMetricVO> provinces(StatisticsQueryDTO query);
    /** blog区间访问排行，不是文章累计热度榜。 */
    List<ArticleViewMetricVO> articles(StatisticsQueryDTO query);
}
