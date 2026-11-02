package com.xuan.service;

import com.xuan.dto.StatisticsQueryDTO;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.PublicStatisticsVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;

import java.util.List;

/**
 * 统计业务接口，公开统计和后台报表分别检查权限，按当前保留记录计算。
 */
public interface IStatisticsService {

    /** 查询博客公开统计，只统计公开内容和 blog 站点。 */
    PublicStatisticsVO publicStatistics();

    /** 查询后台统计总览，内容数量按当前数据统计，访问指标按指定的 UTC 时间段统计。 */
    AdminStatisticsVO overview(StatisticsQueryDTO query);

    /** 查询每日浏览量，按 UTC 日期统计，没有访问的日期补零。 */
    List<ViewTrendVO> viewTrend(StatisticsQueryDTO query);

    /** 查询每日新增及累计访客数，按首次访问日期统计，没有新增的日期补零，累计值从本次查询的 begin 开始计算。 */
    List<VisitorTrendVO> visitorTrend(StatisticsQueryDTO query);

    /** 按省份统计访客数，使用访客当前省份，同一访客只计算一次。 */
    List<ProvinceMetricVO> provinces(StatisticsQueryDTO query);

    /** 查询 blog 站点在指定时间段内的文章访问排行，按该时间段的访问量排名，不使用文章累计热度。 */
    List<ArticleViewMetricVO> articles(StatisticsQueryDTO query);
}
