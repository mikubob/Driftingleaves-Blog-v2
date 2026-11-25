package com.xuan.handle;

import com.xuan.constant.ErrorCode;
import com.xuan.constant.HttpConstants;
import com.xuan.exception.BusinessException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 统一校验接口查询参数与请求体边界，集中管理各路由的参数白名单，替代原先分散的多个 RequestAdvice。
 */
@ControllerAdvice(basePackages = "com.xuan.controller")
public class RequestParamAdvice {

    private static final String GITHUB_OAUTH_ISSUER = "https://github.com/login/oauth";

    /**
     * 除 GET 外不允许携带请求体的明确路由集合。
     */
    private static final Set<String> NO_BODY_ROUTES = Set.of(
            "POST /blog/auth/logout",
            "POST /admin/auth/logout",
            "HEAD /blog/me/link-application",
            "DELETE /blog/me/oauth-bindings/{source}",
            "DELETE /admin/me/oauth-bindings/{source}",
            "DELETE /blog/me/link-application/{linkId}",
            "DELETE /blog/me/messages/{id}",
            "DELETE /blog/me/rss-subscription"
    );

    /**
     * 文件上传路由，单独检查 multipart 结构与大小上限。
     */
    private static final Set<String> UPLOAD_ROUTES = Set.of(
            "POST /admin/files",
            "POST /admin/files/display-images"
    );

    /**
     * 自行处理原始请求体的安全上报路由，不在这里重复拦截。
     */
    private static final Set<String> SELF_VALIDATED_ROUTES = Set.of(
            "POST /blog/security/not-found",
            "POST /admin/security/not-found"
    );

    /**
     * 各查询路由允许的查询参数白名单，未列出的路由默认不接受任何查询参数。
     */
    private static final Map<String, Set<String>> ALLOWED_QUERY_PARAMS = Map.ofEntries(
            // 系统管理与文章分类标签
            Map.entry("GET /admin/users", Set.of("page", "pageSize", "keyword", "status", "roleId")),
            Map.entry("GET /admin/roles", Set.of("page", "pageSize", "keyword", "status")),
            Map.entry("GET /admin/categories", Set.of("page", "pageSize", "keyword", "status")),
            Map.entry("GET /admin/permissions", Set.of("page", "pageSize", "keyword", "module", "status")),
            Map.entry("GET /admin/articles", Set.of("page", "pageSize", "keyword", "categoryId", "tagId",
                    "authorId", "status", "visibility", "trash")),
            Map.entry("GET /blog/articles", Set.of("page", "pageSize", "keyword", "categoryId", "tagId",
                    "month", "authorId")),
            Map.entry("GET /admin/tags", Set.of("page", "pageSize", "keyword")),
            Map.entry("GET /blog/archives", Set.of("page", "pageSize")),
            Map.entry("GET /blog/categories", Set.of("page", "pageSize")),
            Map.entry("GET /blog/tags", Set.of("page", "pageSize")),
            Map.entry("GET /blog/authors", Set.of("page", "pageSize")),
            Map.entry("GET /blog/me/liked-articles", Set.of("page", "pageSize")),
            Map.entry("GET /blog/article-rankings", Set.of("period", "metric", "limit")),
            Map.entry("GET /blog/auth/oauth/{source}/callback",
                    Set.of("state", "code", "error", "error_description", "iss")),
            Map.entry("GET /admin/auth/oauth/{source}/callback",
                    Set.of("state", "code", "error", "error_description", "iss")),

            // 评论与留言
            Map.entry("GET /blog/articles/{articleId}/comments", Set.of("page", "pageSize", "parentId")),
            Map.entry("GET /blog/me/comments", Set.of("page", "pageSize", "articleId", "status")),
            Map.entry("GET /admin/comments/{commentId}/conversation", Set.of("page", "pageSize")),
            Map.entry("GET /admin/comments", Set.of("page", "pageSize", "articleId", "userId", "status", "parentId")),
            Map.entry("GET /blog/messages", Set.of("page", "pageSize", "parentId")),
            Map.entry("GET /blog/me/messages", Set.of("page", "pageSize", "status")),
            Map.entry("GET /admin/messages/{id}/conversation", Set.of("page", "pageSize")),
            Map.entry("GET /admin/messages", Set.of("page", "pageSize", "status", "userId", "isSecret",
                    "begin", "end", "parentId")),

            // 友链、文件、订阅与展示内容
            Map.entry("GET /blog/links", Set.of("page", "pageSize")),
            Map.entry("GET /admin/links", Set.of("page", "pageSize", "keyword", "status", "source", "applicantId")),
            Map.entry("GET /admin/files", Set.of("page", "pageSize", "keyword", "uploaderId", "mimeType")),
            Map.entry("GET /blog/sitemap.xml", Set.of("page")),
            Map.entry("GET /admin/rss-subscriptions", Set.of("page", "pageSize", "email", "userId", "status")),
            Map.entry("GET /admin/home/social-links", Set.of("page", "pageSize", "keyword", "status")),
            Map.entry("GET /admin/cv/skills", Set.of("page", "pageSize", "keyword", "status")),
            Map.entry("GET /admin/music", Set.of("page", "pageSize", "keyword", "status")),
            Map.entry("GET /admin/cv/experiences", Set.of("page", "pageSize", "keyword", "status", "experienceType")),
            Map.entry("GET /cv/experiences", Set.of("page", "pageSize", "experienceType")),
            Map.entry("GET /cv/skills", Set.of("page", "pageSize")),
            Map.entry("GET /blog/music", Set.of("page", "pageSize")),

            // 访客、浏览记录、统计与审计日志
            Map.entry("GET /admin/visitors", Set.of("page", "pageSize", "country", "province", "city", "isBlocked")),
            Map.entry("GET /admin/page-views", Set.of("page", "pageSize", "visitorId", "userId", "articleId",
                    "siteCode", "pagePath", "referer", "begin", "end")),
            Map.entry("GET /admin/statistics/overview", Set.of("begin", "end", "siteCode")),
            Map.entry("GET /admin/statistics/page-views", Set.of("begin", "end", "siteCode")),
            Map.entry("GET /admin/statistics/visitors", Set.of("begin", "end", "siteCode")),
            Map.entry("GET /admin/statistics/provinces", Set.of("begin", "end", "siteCode")),
            Map.entry("GET /admin/statistics/articles", Set.of("begin", "end", "limit")),
            Map.entry("GET /admin/operation-logs", Set.of("page", "pageSize", "userId", "targetId",
                    "operationType", "operationTarget", "result", "begin", "end"))
    );

    /**
     * 在进入控制器方法前校验参数白名单、批量操作头、文件上传结构及非法请求体。
     */
    @ModelAttribute
    public void validate(HttpServletRequest request) throws ServletException, IOException {
        // 1. 跳过错误分派和自行校验的安全上报接口。
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            return;
        }
        String pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        String route = request.getMethod() + " " + pattern;
        if (SELF_VALIDATED_ROUTES.contains(route)) {
            return;
        }

        // 2. 批量操作每条记录单独传版本，拒绝单条版本头和查询参数。
        if (pattern.startsWith("/admin/") && pattern.contains("/batch-")) {
            if (request.getHeader(HttpConstants.HEADER_IF_REVISION) != null || request.getQueryString() != null) {
                throw new BusinessException(400, ErrorCode.VALIDATION_ERROR,
                        "批量操作信息无效，请刷新列表、重新选择记录后再操作。");
            }
            return;
        }

        // 3. 文件上传接口单独校验大小上限和 multipart 表单字段。
        if (UPLOAD_ROUTES.contains(route)) {
            validateFileUpload(request);
            return;
        }

        // 4. GET 和声明无请求体的接口禁止携带内容或分块传输体。
        boolean noBody = "GET".equals(request.getMethod()) || NO_BODY_ROUTES.contains(route);
        if (noBody && hasRequestBody(request)) {
            throw new BusinessException(400, ErrorCode.VALIDATION_ERROR,
                    "当前操作不需要提交内容，请刷新页面后重试。");
        }

        // 5. 校验查询参数是否在白名单内、无重复且非空白；GitHub 回调额外校验 iss。
        Set<String> allowed = ALLOWED_QUERY_PARAMS.getOrDefault(route, Set.of());
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw new BusinessException(400, ErrorCode.VALIDATION_ERROR,
                        "筛选条件包含不支持、重复或空白的项目，请清空筛选后重新选择。");
            }
            if ("iss".equals(name)) {
                validateGithubIssuer(request, values[0]);
            }
        });
    }

    /**
     * 校验文件上传请求，只允许单个 file 和可选的 usage 字段。
     */
    private void validateFileUpload(HttpServletRequest request) throws ServletException, IOException {
        if (request.getContentLengthLong() > HttpConstants.MAX_UPLOAD_REQUEST_BYTES) {
            throw new BusinessException(413, ErrorCode.PAYLOAD_TOO_LARGE,
                    "单次上传不能超过61 MiB，请缩小文件后重试。");
        }
        if (request.getQueryString() != null
                || !(request instanceof MultipartHttpServletRequest multipart)
                || multipart.getMultiFileMap().size() != 1
                || multipart.getFiles("file").size() != 1) {
            throw invalidFileRequest();
        }

        Map<String, String[]> parameters = multipart.getParameterMap();
        if (!parameters.isEmpty()
                && (parameters.size() != 1 || !parameters.containsKey("usage") || parameters.get("usage").length != 1)) {
            throw invalidFileRequest();
        }

        var parts = request.getParts();
        if (!parts.isEmpty()) {
            boolean unexpectedPart = parts.size() != 1 + parameters.size() || parts.stream().anyMatch(part ->
                    part.getSubmittedFileName() == null
                            ? !"usage".equals(part.getName())
                            : !"file".equals(part.getName()));
            if (unexpectedPart) {
                throw invalidFileRequest();
            }
        }
    }

    /**
     * 校验第三方回调中的 iss 参数，只允许 GitHub 官方发行方。
     */
    private void validateGithubIssuer(HttpServletRequest request, String issuer) {
        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(pathVariables instanceof Map<?, ?> variables)
                || !"GITHUB".equals(variables.get("source"))
                || !GITHUB_OAUTH_ISSUER.equals(issuer)) {
            throw new BusinessException(400, ErrorCode.VALIDATION_ERROR,
                    "第三方授权回调的发行方不匹配，请重新发起授权。");
        }
    }

    /**
     * 判断请求是否携带了请求体或分块传输头。
     */
    private boolean hasRequestBody(HttpServletRequest request) {
        return request.getContentLengthLong() > 0
                || request.getHeader(HttpConstants.HEADER_TRANSFER_ENCODING) != null;
    }

    /**
     * 返回统一的文件请求参数错误，不带文件名、存储路径或多余字段内容。
     */
    private BusinessException invalidFileRequest() {
        return new BusinessException(400, ErrorCode.VALIDATION_ERROR,
                "文件请求无效，请刷新页面后重试；上传时请每次选择一个文件。");
    }
}
