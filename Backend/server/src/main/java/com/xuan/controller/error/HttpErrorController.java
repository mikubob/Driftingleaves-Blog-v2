package com.xuan.controller.error;

import com.xuan.result.Result;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servlet 容器统一错误接口
 */
@Slf4j
@RestController
public class HttpErrorController implements ErrorController {

    /**
     * 处理容器分发错误，保留原 HTTP 状态码并返回统一错误结构。
     *
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    @RequestMapping("/error")
    public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code ? code : 500;
        String code = mapErrorCode(status);
        log.warn("容器错误处理: status={}, code={}", status, code);
        return ResponseEntity.status(status).body(Result.error(code, "请求处理失败"));
    }

    /**
     * 将 HTTP 状态码映射为公开错误码。
     *
     * @param status HTTP 状态码
     * @return 错误码
     */
    private String mapErrorCode(int status) {
        if (status == 400) {
            return "VALIDATION_ERROR";
        }
        if (status == 404) {
            return "NOT_FOUND";
        }
        if (status == 405) {
            return "METHOD_NOT_ALLOWED";
        }
        if (status == 413) {
            return "PAYLOAD_TOO_LARGE";
        }
        if (status == 415) {
            return "UNSUPPORTED_MEDIA_TYPE";
        }
        return "INTERNAL_ERROR";
    }
}
