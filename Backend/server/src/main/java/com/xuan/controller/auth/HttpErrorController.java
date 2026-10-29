package com.xuan.controller.auth;

import com.xuan.result.Result;
import org.springframework.boot.webmvc.error.ErrorController;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servlet 错误分派仍使用统一结果，不回显容器的内部路径或异常。 */
@RestController
public class HttpErrorController implements ErrorController {
    /**
     * 将 Servlet 容器错误分派转换为统一 JSON 响应。
     * <p>保留错误分派状态，只映射公开错误码和通用文案，不输出异常消息、堆栈或内部请求路径。</p>
     *
     * @param request 由容器携带错误状态属性的当前请求
     * @return 包含实际 HTTP 状态的统一错误结果
     */
    @RequestMapping("/error")
    public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
        // 1. 读取容器错误分派的 HTTP 状态，缺失时按内部错误处理。
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code ? code : 500;
        // 2. 将框架错误映射为协议错误码，不透传容器异常或内部路径。
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
