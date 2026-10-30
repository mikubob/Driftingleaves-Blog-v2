package com.xuan.handle;

import com.xuan.exception.BusinessException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;

/** 文件入口拒绝额外查询、重复文件和伪造上传者，不能让 MVC 静默忽略 multipart 字段。 */
@ControllerAdvice(assignableTypes = {com.xuan.controller.admin.FileController.class,
        com.xuan.controller.blog.FileController.class})
public class FileRequestAdvice {

    /** POST 只接收一个 file 部件，其他接口没有请求体，分页才有明确查询字段。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) throws ServletException, IOException {
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if ("POST /admin/files".equals(route)) {
            if (request.getContentLengthLong() > 11 * 1024 * 1024) {
                throw new BusinessException(413, "PAYLOAD_TOO_LARGE", "multipart请求超过11MiB");
            }
            if (request.getQueryString() != null || !(request instanceof MultipartHttpServletRequest multipart)
                    || !multipart.getParameterMap().isEmpty() || multipart.getMultiFileMap().size() != 1
                    || multipart.getFiles("file").size() != 1) {
                throw invalid();
            }
            var parts = request.getParts();
            if (!parts.isEmpty() && (parts.size() != 1 || parts.stream().anyMatch(part ->
                    !"file".equals(part.getName()) || part.getSubmittedFileName() == null))) {
                throw invalid();
            }
            return;
        }
        if (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null) {
            throw invalid();
        }
        Set<String> allowed = "GET /admin/files".equals(route)
                ? Set.of("page", "pageSize", "keyword", "uploaderId", "mimeType") : Set.of();
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1 || values[0].isBlank()) {
                throw invalid();
            }
        });
    }

    /** 参数错误只返回固定消息，不带文件名、存储路径或额外字段内容。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "请求含未声明、重复的字段或不支持的请求体");
    }
}
