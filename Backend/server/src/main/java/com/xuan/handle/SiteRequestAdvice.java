package com.xuan.handle;

import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Set;

/** 第十章拒绝未声明、重复和空查询参数，不让 MVC 静默忽略状态或身份字段。 */
@ControllerAdvice(assignableTypes = {com.xuan.controller.blog.FriendLinkController.class,
        com.xuan.controller.blog.SiteConfigController.class, com.xuan.controller.admin.FriendLinkController.class,
        com.xuan.controller.admin.SiteConfigController.class})
public class SiteRequestAdvice {
    /** 值的类型和长度交给 DTO，GET 不接受请求体。 */
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
