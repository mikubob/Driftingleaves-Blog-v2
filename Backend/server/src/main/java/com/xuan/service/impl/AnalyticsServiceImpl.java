package com.xuan.service.impl;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.entity.BlogPageView;
import com.xuan.entity.BlogVisitor;
import com.xuan.exception.BusinessException;
import com.xuan.location.IpLocationResolver;
import com.xuan.mapper.ArticleMapper;
import com.xuan.mapper.PageViewMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.mapper.VisitorMapper;
import com.xuan.model.ArticleRow;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.ratelimit.AuthRateLimiter;
import com.xuan.service.IAnalyticsService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.IpUtils;
import com.xuan.utils.SitePathUtils;
import com.xuan.vo.TrackingResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 可信来源、PUBLIC文章和可选会话核验通过后，才写访客与一条明细。
 */
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements IAnalyticsService {

    private final PublicSiteProperties sites;
    private final VisitorMapper visitors;
    private final PageViewMapper views;
    private final ArticleMapper articles;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final IpLocationResolver locations;
    private final Clock clock;
    private final AuthRateLimiter limiter;

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrackingResultVO track(PageViewCreateDTO request, String origin, String ip, String userAgent) {
        // 1. 先核验站点来源和规范路径；携带 Token 时还要检查有效身份。
        String path = SitePathUtils.path(request.getPagePath());
        sites.requireSite(request.getSiteCode(), origin, path);
        String referer = SitePathUtils.referer(request.getReferer());
        Long actor = current();
        limiter.analytics(ip, request.getFingerprint(), actor);
        if (actor != null) {
            users.lockById(actor);
            current();
        }

        // 2. 文章详情路径即使没传关联 ID 也要核验 PUBLIC，避免记录私密或不存在的文章。
        String prefix = "blog".equals(request.getSiteCode()) ? sites.base("blog").getPath() + "/articles/" : "";
        if ("blog".equals(request.getSiteCode()) && path.startsWith(prefix)) {
            String slug = path.substring(prefix.length());
            ArticleRow article = articles.publicBySlug(slug, now(), true);
            current();
            if (article == null || articles.publicById(article.getArticleId(), now()) == null) {
                throw new BusinessException(404, "NOT_FOUND", "文章不存在");
            }
            if (!path.equals(sites.base("blog").getPath() + "/articles/" + article.getSlug())
                    || request.getArticleId() != null && !request.getArticleId().equals(article.getArticleId())) {
                throw invalid();
            }
        } else if (request.getArticleId() != null) {
            throw invalid();
        }

        // 3. 锁住统计标识后检查封禁；原 IP 只用于地域查询，落库前先脱敏。
        LocalDateTime at = now();
        visitors.ensure(request.getFingerprint(), at);
        BlogVisitor visitor = visitors.lockFingerprint(request.getFingerprint());
        current();
        if (visitor.getIsBlocked() == 1 && (visitor.getBlockedUntil() == null || visitor.getBlockedUntil()
                .isAfter(now()))) {
            throw new BusinessException(403, "VISITOR_BLOCKED", "此统计标识暂不接收采集");
        }
        IpLocationResolver.GeoInfo geo = locations.getGeoInfo(ip);
        String masked = IpUtils.anonymize(ip);
        String agent = userAgent == null ? null : userAgent.codePoints().filter(c -> !Character.isISOControl(c))
                .limit(1024)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        visitor.setIpAddress(masked);
        visitor.setUserAgent(agent);
        visitor.setCountry(geo.country());
        visitor.setProvince(geo.province());
        visitor.setCity(geo.city());
        visitor.setLastVisitTime(at);
        visitors.visit(visitor);

        // 4. 同一事务保存一次浏览明细，不增加文章累计阅读，避免两个入口重复计数。
        BlogPageView row = new BlogPageView();
        row.setVisitorId(visitor.getVisitorId());
        row.setUserId(actor);
        row.setArticleId(request.getArticleId());
        row.setSiteCode(request.getSiteCode());
        row.setPagePath(path);
        row.setPageTitle(request.getPageTitle());
        row.setReferer(referer);
        row.setIpAddress(masked);
        row.setUserAgent(agent);
        row.setViewTime(at);
        views.insert(row);
        return new TrackingResultVO(true);
    }

    /** 可选Bearer一旦出现就必须有效，等待行锁后不能沿用旧Token。 */
    private Long current() {
        if (sessions.currentToken() == null) {
            return null;
        }
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        return actor;
    }

    /** 浏览采集不使用执行机器本地时间，也不增加文章累计阅读。 */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    /** 不回显客户端路径或ID。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "文章关联与访问路径不匹配");
    }
}
