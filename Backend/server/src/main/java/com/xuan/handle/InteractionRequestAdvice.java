package com.xuan.handle;

import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** 第9章只接收文档声明的查询参数，避免 userId 等身份字段被 MVC 静默忽略。 */
@ControllerAdvice(assignableTypes = {com.xuan.controller.blog.CommentController.class,
        com.xuan.controller.me.CommentController.class, com.xuan.controller.admin.CommentController.class,
        com.xuan.controller.blog.ArticleLikeController.class})
public class InteractionRequestAdvice {

    /** 只在绑定前查一次参数名和重复值；值的类型、长度由各入口原有校验负责。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        if ("GET".equals(request.getMethod()) && request.getContentLengthLong() > 0) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Set<String> allowed = switch (route) {
            case "GET /blog/articles/{articleId}/comments" -> Set.of("page", "pageSize", "parentId");
            case "GET /me/comments" -> Set.of("page", "pageSize", "articleId", "status");
            case "GET /admin/comments" -> Set.of("page", "pageSize", "articleId", "userId", "status");
            default -> Set.of();
        };
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "请求含未声明或重复的查询参数");
            }
        });
    }
}
