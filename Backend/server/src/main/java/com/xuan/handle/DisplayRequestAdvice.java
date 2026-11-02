package com.xuan.handle;

import com.xuan.controller.admin.ExperienceController;
import com.xuan.controller.admin.HomeProfileController;
import com.xuan.controller.admin.MusicController;
import com.xuan.controller.admin.OperationLogController;
import com.xuan.controller.admin.PageViewController;
import com.xuan.controller.admin.SkillController;
import com.xuan.controller.admin.SocialController;
import com.xuan.controller.admin.StatisticsController;
import com.xuan.controller.admin.SubscriptionController;
import com.xuan.controller.admin.VisitorController;
import com.xuan.controller.blog.AnalyticsController;
import com.xuan.controller.blog.FeedController;
import com.xuan.controller.blog.MessageController;
import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** 检查展示类接口的请求，拒绝多余查询参数以及 GET、DELETE 请求体。 */
@ControllerAdvice(assignableTypes = {HomeProfileController.class,
        SocialController.class,
        ExperienceController.class,
        SkillController.class,
        MusicController.class,
        com.xuan.controller.publicsite.PublicHomeProfileController.class,
        com.xuan.controller.publicsite.PublicSocialController.class,
        com.xuan.controller.cv.ExperienceController.class,
        com.xuan.controller.cv.SkillController.class,
        com.xuan.controller.blog.MusicController.class, OperationLogController.class,
        SubscriptionController.class,
        com.xuan.controller.blog.MySubscriptionController.class, com.xuan.controller.blog.SubscriptionController.class,
        FeedController.class, MessageController.class,
        com.xuan.controller.blog.MyMessageController.class, com.xuan.controller.admin.MessageController.class,
        AnalyticsController.class, com.xuan.controller.blog.OnlineController.class,
        com.xuan.controller.home.AnalyticsController.class, com.xuan.controller.home.OnlineController.class,
        com.xuan.controller.cv.AnalyticsController.class, com.xuan.controller.cv.OnlineController.class,
        VisitorController.class, PageViewController.class,
        StatisticsController.class, com.xuan.controller.blog.StatisticsController.class})
public class DisplayRequestAdvice {

    /** 检查查询参数和请求体，拒绝重复参数、未知参数和不允许的请求体；JSON 字段由 DTO 校验。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        if (("GET".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))
                && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Set<String> allowed = switch (route) {
            case "GET /admin/visitors" -> Set.of("page", "pageSize", "country", "province", "city", "isBlocked");
            case "GET /admin/page-views" ->
                    Set.of("page", "pageSize", "visitorId", "userId", "articleId", "siteCode", "pagePath", "referer",
                            "begin", "end");
            case "GET /admin/statistics/overview", "GET /admin/statistics/page-views", "GET /admin/statistics/visitors",
                 "GET /admin/statistics/provinces" -> Set.of("begin", "end", "siteCode");
            case "GET /admin/statistics/articles" -> Set.of("begin", "end", "limit");
            case "GET /blog/messages" -> Set.of("page", "pageSize", "parentId");
            case "GET /blog/me/messages" -> Set.of("page", "pageSize", "status");
            case "GET /admin/messages" -> Set.of("page", "pageSize", "status", "userId", "isSecret", "begin", "end");
            case "GET /blog/sitemap.xml" -> Set.of("page");
            case "GET /admin/rss-subscriptions" -> Set.of("page", "pageSize", "email", "userId", "status");
            case "GET /admin/operation-logs" ->
                    Set.of("page", "pageSize", "userId", "targetId", "operationType", "operationTarget", "result",
                            "begin", "end");
            case "GET /admin/home/social-links", "GET /admin/cv/skills", "GET /admin/music" ->
                    Set.of("page", "pageSize", "keyword", "status");
            case "GET /admin/cv/experiences" -> Set.of("page", "pageSize", "keyword", "status", "experienceType");
            case "GET /cv/experiences" -> Set.of("page", "pageSize", "experienceType");
            case "GET /cv/skills", "GET /blog/music" -> Set.of("page", "pageSize");
            default -> Set.of();
        };
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "请求含未声明、重复或空白的查询参数");
            }
        });
    }
}
