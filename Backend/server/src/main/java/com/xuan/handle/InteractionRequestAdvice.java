package com.xuan.handle;

import com.xuan.controller.blog.ArticleLikeController;
import com.xuan.controller.blog.CommentController;
import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** 检查评论等互动接口的参数，拒绝未声明的 userId 等字段。 */
@ControllerAdvice(assignableTypes = {CommentController.class,
        com.xuan.controller.blog.MyCommentController.class, com.xuan.controller.admin.CommentController.class,
        ArticleLikeController.class})
public class InteractionRequestAdvice {

    /** 检查参数名和重复参数，参数类型及长度仍由接口原有校验负责。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        if ("GET".equals(request.getMethod()) && request.getContentLengthLong() > 0) {
            throw new BusinessException(400, "VALIDATION_ERROR", "当前操作不需要提交内容，请刷新页面后重试。");
        }
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Set<String> allowed = switch (route) {
            case "GET /blog/articles/{articleId}/comments" -> Set.of("page", "pageSize", "parentId");
            case "GET /blog/me/comments" -> Set.of("page", "pageSize", "articleId", "status");
            case "GET /admin/comments/{commentId}/conversation" -> Set.of("page", "pageSize");
            case "GET /admin/comments" -> Set.of("page", "pageSize", "articleId", "userId", "status", "parentId");
            default -> Set.of();
        };
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "筛选条件包含不支持、重复或空白的项目，请清空筛选后重新选择。");
            }
        });
    }
}
