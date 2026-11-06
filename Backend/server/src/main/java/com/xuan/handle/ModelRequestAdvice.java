package com.xuan.handle;

import com.xuan.controller.admin.ArticleController;
import com.xuan.controller.admin.CategoryController;
import com.xuan.controller.admin.MenuController;
import com.xuan.controller.admin.PermissionController;
import com.xuan.controller.admin.RoleController;
import com.xuan.controller.admin.TagController;
import com.xuan.controller.admin.UserController;
import com.xuan.controller.auth.CaptchaController;
import com.xuan.controller.blog.AuthController;
import com.xuan.controller.blog.AuthorController;
import com.xuan.controller.blog.MeController;
import com.xuan.controller.blog.MyArticleController;
import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** 检查登录、系统管理和文章分类标签接口的参数，拒绝未声明的字段。 */
@ControllerAdvice(assignableTypes = {AuthController.class, CaptchaController.class,
        com.xuan.controller.admin.AuthController.class, com.xuan.controller.admin.MeController.class,
        MeController.class, UserController.class,
        RoleController.class, PermissionController.class,
        MenuController.class, ArticleController.class,
        CategoryController.class, TagController.class,
        com.xuan.controller.blog.ArticleController.class, com.xuan.controller.blog.CategoryController.class,
        com.xuan.controller.blog.TagController.class, AuthorController.class,
        MyArticleController.class})
public class ModelRequestAdvice {

    /** 检查参数名、重复参数和不允许的请求体，字段内容由 DTO 和业务层校验。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        boolean noBody = "GET".equals(request.getMethod())
                || "POST /blog/auth/logout".equals(route) || "POST /admin/auth/logout".equals(route)
                || "DELETE /blog/me/oauth-bindings/{source}".equals(route)
                || "DELETE /admin/me/oauth-bindings/{source}".equals(route);
        if (noBody && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "当前操作不需要提交内容，请刷新页面后重试。");
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
                throw new BusinessException(400, "VALIDATION_ERROR", "筛选条件包含不支持、重复或空白的项目，请清空筛选后重新选择。");
            }
        });
    }
}
