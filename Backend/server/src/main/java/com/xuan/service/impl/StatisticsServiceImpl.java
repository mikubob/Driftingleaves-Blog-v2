package com.xuan.service.impl;

import com.xuan.dto.StatisticsQueryDTO;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.StatisticsMapper;
import com.xuan.ratelimit.AuthRateLimiter;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IStatisticsService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.vo.AdminStatisticsVO;
import com.xuan.vo.ArticleViewMetricVO;
import com.xuan.vo.ProvinceMetricVO;
import com.xuan.vo.PublicStatisticsVO;
import com.xuan.vo.ViewTrendVO;
import com.xuan.vo.VisitorTrendVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.math.BigInteger;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询统计报表，在同一个只读事务中读取；按 UTC 日期统计时包含起止两天，明细 SQL 的时间范围包含开始时刻、不包含结束时刻。
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements IStatisticsService {
    private final StatisticsMapper statistics;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final Clock clock;
    private final AuthRateLimiter limiter;
    private final ObjectProvider<HttpServletRequest> requests;

    /** 查询博客公开统计。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(cacheNames = "statistics", key = "@publicCacheKeyGenerator.key(#root.methodName, #root.args)", unless = "#result == null")
    public PublicStatisticsVO publicStatistics() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return statistics.publicStatistics(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), now());
    }

    /** 查询后台统计总览。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminStatisticsVO overview(StatisticsQueryDTO query) {
        requireAdmin();
        Range range = range(query);
        limitReport();
        return statistics.overview(range.lower(), range.upper(), query.siteCode(), now());
    }

    /** 查询每日浏览量。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ViewTrendVO> viewTrend(StatisticsQueryDTO query) {
        requireAdmin();
        Range range = range(query);
        limitReport();
        Map<LocalDate, ViewTrendVO> rows = new HashMap<>();
        statistics.viewTrend(range.lower(), range.upper(), query.siteCode()).forEach(row -> rows.put(row.date(), row));
        List<ViewTrendVO> result = new ArrayList<>();
        for (LocalDate day = range.begin(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            result.add(rows.getOrDefault(day, new ViewTrendVO(day, "0", "0")));
        }
        return result;
    }

    /** 查询每日新增及累计访客数。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<VisitorTrendVO> visitorTrend(StatisticsQueryDTO query) {
        requireAdmin();
        Range range = range(query);
        limitReport();
        Map<LocalDate, VisitorTrendVO> rows = new HashMap<>();
        statistics.visitorTrend(range.lower(), range.upper(), query.siteCode()).forEach(row -> rows.put(row.date(), row));
        List<VisitorTrendVO> result = new ArrayList<>();
        BigInteger cumulative = BigInteger.ZERO;
        for (LocalDate day = range.begin(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            String count = rows.containsKey(day) ? rows.get(day).newVisitorCount() : "0";
            cumulative = cumulative.add(new BigInteger(count));
            result.add(new VisitorTrendVO(day, count, cumulative.toString()));
        }
        return result;
    }

    /** 按省份统计访客数。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProvinceMetricVO> provinces(StatisticsQueryDTO query) {
        requireAdmin();
        Range range = range(query);
        limitReport();
        return statistics.provinces(range.lower(), range.upper(), query.siteCode());
    }

    /** 查询指定日期内的文章访问排行。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ArticleViewMetricVO> articles(StatisticsQueryDTO query) {
        requireAdmin();
        Range range = range(query);
        limitReport();
        return statistics.articles(range.lower(), range.upper(), query.limit());
    }

    /** 检查后台报表频率，只统计 HTTP 请求，直接业务调用不读取请求信息。 */
    private void limitReport() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return;
        }
        limiter.report(requests.getObject(), sessions.currentUserId());
    }

    /** 检查统计查询权限，公开接口不支持指定日期，后台需要入口权限和本次报表的 ALL 范围。 */
    private void requireAdmin() {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        if (!"ALL".equals(authorization.requireScope(actor, "blog:statistics:query"))) {
            throw new BusinessException(403, "FORBIDDEN", "此操作需要ALL范围");
        }
    }

    /** 检查统计日期范围，起止日期必须一起传，最多 367 天；结束于 9999 年时不向数据库传入 10000 年。 */
    private Range range(StatisticsQueryDTO query) {
        try {
            if ((query.begin() == null) != (query.end() == null)) {
                throw new IllegalArgumentException();
            }
            LocalDate begin = query.begin() == null ? LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC) : date(query.begin());
            LocalDate end = query.end() == null ? begin : date(query.end());
            if (end.isBefore(begin) || ChronoUnit.DAYS.between(begin, end) > 366) {
                throw new IllegalArgumentException();
            }
            LocalDateTime upper = end.equals(LocalDate.of(9999, 12, 31)) ? null : end.plusDays(1).atStartOfDay();
            return new Range(begin, end, begin.atStartOfDay(), upper);
        } catch (IllegalArgumentException | DateTimeException error) {
            throw new BusinessException(400, "VALIDATION_ERROR", "日期区间须成对且最多367天");
        }
    }

    /** 解析统计日期，不接受带时间的值、无效闰日或超出数据库范围的年份。 */
    private LocalDate date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException();
        }
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() < 1000 || date.getYear() > 9999) {
            throw new IllegalArgumentException();
        }
        return date;
    }

    /** 获取公开文章查询使用的 UTC 截止时间。 */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 只在本次报表调用内部传递时间条件。
     */
    private record Range(LocalDate begin, LocalDate end, LocalDateTime lower, LocalDateTime upper) { }
}
