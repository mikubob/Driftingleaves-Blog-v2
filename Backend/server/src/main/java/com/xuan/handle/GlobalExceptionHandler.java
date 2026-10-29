package com.xuan.handle;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.List;

/** HTTP 错误转换；仅输出可公开文案，不回显输入、SQL、平台响应或堆栈。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException exception) {
        // 1. 保留业务定义的 HTTP 状态，不能把失败包装成 200。
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        // 2. 限流错误补充等待秒数，其余错误沿用统一脱敏响应。
        if (exception.getRetryAfter() != null) {
            response.header("Retry-After", exception.getRetryAfter().toString());
        }
        return response.body(Result.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException exception) {
        // 仅提取字段名和约束说明，不把被拒绝的密码等输入值放入错误列表。
        List<Result.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new Result.FieldError(error.getField(), error.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new Result<>("VALIDATION_ERROR", "参数校验失败", null, errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestValueException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<Result<Void>> invalid(Exception exception) {
        return error(400, "VALIDATION_ERROR", "请求格式或参数不符合要求");
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> unauthenticated() {
        return error(401, "UNAUTHENTICATED", "请重新登录");
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> forbidden() {
        return error(403, "FORBIDDEN", "没有操作权限");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> unique() {
        return error(409, "UNIQUE_CONFLICT", "唯一字段已被使用");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Result<Void>> unavailable() {
        return error(503, "SERVICE_UNAVAILABLE", "基础服务暂不可用");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> missing() {
        return error(404, "NOT_FOUND", "接口不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> method() {
        return error(405, "METHOD_NOT_ALLOWED", "请求方法不支持");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> mediaType() {
        return error(415, "UNSUPPORTED_MEDIA_TYPE", "请求媒体类型不支持");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception exception) {
        // 只记录异常类型，避免平台、SQL或解析器的异常消息携带秘密输入。
        System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR,
                "未预期异常类型：" + exception.getClass().getName());
        return error(500, "INTERNAL_ERROR", "服务内部错误");
    }

    private ResponseEntity<Result<Void>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Result.error(code, message));
    }
}
