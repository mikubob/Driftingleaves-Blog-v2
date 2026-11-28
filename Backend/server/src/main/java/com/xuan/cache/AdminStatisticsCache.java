package com.xuan.cache;

import com.xuan.constant.CacheNames;
import com.xuan.mapper.StatisticsMapper;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.RefererMetricVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存后台统计报表查询结果，按是否历史区间区分 60 秒短缓存与 30 分钟长缓存。
 */
@Component
@RequiredArgsConstructor
public class AdminStatisticsCache {

    private final StatisticsMapper statistics;

    /**
     * 查询后台统计总览；因包含实时文章与待审计数，统一使用短 TTL 缓存。
     */
    @Cacheable(
            cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
            cacheNames = CacheNames.ADMIN_STATS,
            key = "'overview:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
            unless = "#result == null"
    )
    public AdminStatisticsVO overview(LocalDate begin, LocalDate end, LocalDateTime lower,
            LocalDateTime upper, String siteCode, LocalDateTime now) {
        return statistics.overview(lower, upper, siteCode, now);
    }

    /**
     * 查询每日浏览量趋势，历史区间使用长缓存，包含今日的区间使用短缓存。
     */
    @Caching(cacheable = {
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS_HISTORY,
                    key = "'viewTrend:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
                    condition = "#historical",
                    unless = "#result == null"
            ),
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS,
                    key = "'viewTrend:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
                    condition = "!#historical",
                    unless = "#result == null"
            )
    })
    public List<ViewTrendVO> viewTrend(LocalDate begin, LocalDate end, LocalDateTime lower,
            LocalDateTime upper, String siteCode, boolean historical) {
        Map<LocalDate, ViewTrendVO> rows = new HashMap<>();
        statistics.viewTrend(lower, upper, siteCode).forEach(row -> rows.put(row.getDate(), row));
        List<ViewTrendVO> result = new ArrayList<>();
        for (LocalDate day = begin; !day.isAfter(end); day = day.plusDays(1)) {
            result.add(rows.getOrDefault(day, ViewTrendVO.builder()
                    .date(day)
                    .pageViewCount("0")
                    .visitorCount("0")
                    .build()));
        }
        return result;
    }

    /**
     * 查询每日新增及累计访客趋势，历史区间使用长缓存，包含今日的区间使用短缓存。
     */
    @Caching(cacheable = {
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS_HISTORY,
                    key = "'visitorTrend:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
                    condition = "#historical",
                    unless = "#result == null"
            ),
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS,
                    key = "'visitorTrend:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
                    condition = "!#historical",
                    unless = "#result == null"
            )
    })
    public List<VisitorTrendVO> visitorTrend(LocalDate begin, LocalDate end, LocalDateTime lower,
            LocalDateTime upper, String siteCode, boolean historical) {
        Map<LocalDate, VisitorTrendVO> rows = new HashMap<>();
        statistics.visitorTrend(lower, upper, siteCode).forEach(row -> rows.put(row.getDate(), row));
        List<VisitorTrendVO> result = new ArrayList<>();
        BigInteger cumulative = BigInteger.ZERO;
        for (LocalDate day = begin; !day.isAfter(end); day = day.plusDays(1)) {
            String count = rows.containsKey(day) ? rows.get(day).getNewVisitorCount() : "0";
            cumulative = cumulative.add(new BigInteger(count));
            result.add(VisitorTrendVO.builder()
                    .date(day)
                    .newVisitorCount(count)
                    .cumulativeNewVisitorCount(cumulative.toString())
                    .build());
        }
        return result;
    }

    /**
     * 按省份统计访客分布，历史区间使用长缓存，包含今日的区间使用短缓存。
     */
    @Caching(cacheable = {
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS_HISTORY,
                    key = "'provinces:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
                    condition = "#historical",
                    unless = "#result == null"
            ),
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS,
                    key = "'provinces:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode)",
                    condition = "!#historical",
                    unless = "#result == null"
            )
    })
    public List<ProvinceMetricVO> provinces(LocalDate begin, LocalDate end, LocalDateTime lower,
            LocalDateTime upper, String siteCode, boolean historical) {
        return statistics.provinces(lower, upper, siteCode);
    }

    /**
     * 查询文章访问排行，历史区间使用长缓存，包含今日的区间使用短缓存。
     */
    @Caching(cacheable = {
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS_HISTORY,
                    key = "'articles:' + #begin + ':' + #end + ':' + #limit",
                    condition = "#historical",
                    unless = "#result == null"
            ),
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS,
                    key = "'articles:' + #begin + ':' + #end + ':' + #limit",
                    condition = "!#historical",
                    unless = "#result == null"
            )
    })
    public List<ArticleViewMetricVO> articles(LocalDate begin, LocalDate end, LocalDateTime lower,
            LocalDateTime upper, int limit, boolean historical) {
        return statistics.articles(lower, upper, limit);
    }

    /**
     * 查询访问来源域名排行，历史区间使用长缓存，包含今日的区间使用短缓存。
     */
    @Caching(cacheable = {
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS_HISTORY,
                    key = "'referers:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode) + ':' + #limit",
                    condition = "#historical",
                    unless = "#result == null"
            ),
            @Cacheable(
                    cacheManager = CacheNames.SIMPLE_CACHE_MANAGER,
                    cacheNames = CacheNames.ADMIN_STATS,
                    key = "'referers:' + #begin + ':' + #end + ':' + (#siteCode == null ? '' : #siteCode) + ':' + #limit",
                    condition = "!#historical",
                    unless = "#result == null"
            )
    })
    public List<RefererMetricVO> referers(LocalDate begin, LocalDate end, LocalDateTime lower,
            LocalDateTime upper, String siteCode, int limit, boolean historical) {
        return statistics.referers(lower, upper, siteCode, limit);
    }
}
