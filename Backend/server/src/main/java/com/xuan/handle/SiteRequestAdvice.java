package com.xuan.handle;

import com.xuan.controller.blog.FriendLinkController;
import com.xuan.controller.publicsite.PublicSiteConfigController;
import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** 检查站点接口参数，拒绝未声明、重复和空参数，包括多余的状态或身份字段。 */
@ControllerAdvice(assignableTypes = {FriendLinkController.class,
        PublicSiteConfigController.class, com.xuan.controller.admin.FriendLinkController.class,
        com.xuan.controller.admin.SiteConfigController.class})
public class SiteRequestAdvice {

    /** 检查查询参数和 GET 请求体，参数类型及长度由 DTO 校验。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        if ("GET".equals(request.getMethod()) && (request.getContentLengthLong() > 0
                || request.getHeader("Transfer-Encoding") != null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Set<String> allowed = switch (route) {
            case "GET /blog/links" -> Set.of("page", "pageSize");
            case "GET /admin/links" -> Set.of("page", "pageSize", "keyword", "status");
            default -> Set.of();
        };
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "请求含未声明或重复的查询参数");
            }
        });
    }
}
