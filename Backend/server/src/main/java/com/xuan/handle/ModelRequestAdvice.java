package com.xuan.handle;

import com.xuan.controller.admin.ArticleController;
import com.xuan.controller.admin.CategoryController;
import com.xuan.controller.admin.MenuController;
import com.xuan.controller.admin.PermissionController;
import com.xuan.controller.admin.RoleController;
import com.xuan.controller.admin.TagController;
import com.xuan.controller.admin.UserController;
import com.xuan.controller.blog.AuthController;
import com.xuan.controller.blog.AuthorController;
import com.xuan.controller.blog.MeController;
import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** 身份、系统管理和文章字典只收声明过的参数，不能把忽略身份字段当作校验通过。 */
@ControllerAdvice(assignableTypes = {AuthController.class, com.xuan.controller.auth.CaptchaController.class,
        com.xuan.controller.admin.AuthController.class, com.xuan.controller.admin.MeController.class,
        MeController.class, UserController.class,
        RoleController.class, PermissionController.class,
        MenuController.class, ArticleController.class,
        CategoryController.class, TagController.class,
        com.xuan.controller.blog.ArticleController.class, com.xuan.controller.blog.CategoryController.class,
        com.xuan.controller.blog.TagController.class, AuthorController.class,
        com.xuan.controller.blog.MyArticleController.class})
public class ModelRequestAdvice {

    /** 绑定前只查参数名、重复值和无请求体约定，字段内容仍交给 DTO 和业务层检查。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        boolean noBody = "GET".equals(request.getMethod())
                || "POST /blog/auth/logout".equals(route) || "POST /admin/auth/logout".equals(route)
                || "DELETE /blog/me/oauth-bindings/{source}".equals(route)
                || "DELETE /admin/me/oauth-bindings/{source}".equals(route)
                || "DELETE /admin/articles/{articleId}/permanent".equals(route);
        if (noBody && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        Set<String> allowed = switch (route) {
            case "GET /admin/users" -> Set.of("page", "pageSize", "keyword", "status", "roleId");
            case "GET /admin/roles", "GET /admin/categories" -> Set.of("page", "pageSize", "keyword", "status");
            case "GET /admin/permissions" -> Set.of("page", "pageSize", "keyword", "module", "status");
            case "GET /admin/articles" -> Set.of("page", "pageSize", "keyword", "categoryId", "tagId", "authorId",
                    "status", "visibility", "trash");
            case "GET /blog/articles" -> Set.of("page", "pageSize", "keyword", "categoryId", "tagId", "month", "authorId");
            case "GET /admin/tags" -> Set.of("page", "pageSize", "keyword");
            case "GET /blog/archives", "GET /blog/categories", "GET /blog/tags", "GET /blog/authors",
                 "GET /blog/me/liked-articles" -> Set.of("page", "pageSize");
            case "GET /blog/article-rankings" -> Set.of("period", "metric", "limit");
            // 平台错误说明可以带回，但不传给业务层，也不写日志或回跳地址。
            case "GET /blog/auth/oauth/{source}/callback", "GET /admin/auth/oauth/{source}/callback" -> Set.of("state", "code", "error", "error_description");
            default -> Set.of();
        };
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "请求含未声明、重复或空白的查询参数");
            }
        });
    }
}
