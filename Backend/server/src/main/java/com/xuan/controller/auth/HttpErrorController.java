package com.xuan.controller.auth;

import com.xuan.result.Result;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servlet 错误也用统一响应，不把容器内部路径或异常返回给用户。
 */
@RestController
public class HttpErrorController implements ErrorController {

    /**
     * 把容器错误转成统一 JSON，保留原 HTTP 状态；只返回公开错误码和固定文案，不带异常、堆栈或内部路径。
     *
     * @param request 由容器携带错误状态属性的当前请求
     * @return 包含实际 HTTP 状态的统一错误结果
     */
    @RequestMapping("/error")
    public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
        // 1. 先取容器给的错误状态，没提供就按内部错误处理。
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code ? code : 500;

        // 2. 把框架错误转成约定错误码，不直接返回容器异常或内部路径。
        String code = switch (status) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
        return ResponseEntity.status(status).body(Result.error(code, "请求处理失败"));
    }
}
