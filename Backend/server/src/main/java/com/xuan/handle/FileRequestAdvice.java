package com.xuan.handle;

import com.xuan.controller.admin.FileController;
import com.xuan.exception.BusinessException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;

/** 检查文件接口参数，拒绝多余参数、重复文件和客户端指定的上传者。 */
@ControllerAdvice(assignableTypes = {FileController.class,
        com.xuan.controller.blog.FileController.class})
public class FileRequestAdvice {

    /** 检查文件请求，上传只收一个 file，删除使用 JSON 参数，读取不收请求体和多余参数。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) throws ServletException, IOException {
        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if ("POST /admin/files/batch-delete".equals(route)) {
            return;
        }
        if ("POST /admin/files".equals(route)) {
            if (request.getContentLengthLong() > 61 * 1024 * 1024) {
                throw new BusinessException(413, "PAYLOAD_TOO_LARGE", "单次上传不能超过61 MiB，请缩小文件后重试。");
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

    /** 返回文件参数错误，不带文件名、存储路径或多余字段的内容。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "文件请求无效，请刷新页面后重试；上传时请每次选择一个文件。");
    }
}
