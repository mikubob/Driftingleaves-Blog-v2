package com.xuan.handle;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 把错误转成 HTTP 响应，只给用户能看的文案，不带输入、SQL、平台响应或堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 容器 multipart 上限也转换为统一 413，不回显内部临时目录。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> uploadTooLarge() {
        return error(413, "PAYLOAD_TOO_LARGE", "文件或multipart请求超限");
    }

    /** 部件不完整等 multipart 格式错误不应变成内部错误。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> multipart() {
        return error(400, "VALIDATION_ERROR", "multipart请求格式不符合要求");
    }

    /**
     * 保留业务错误状态和错误码，限流时补充 Retry-After。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException exception) {
        // 1. 保留业务错误的 HTTP 状态，不能把失败包成 200。
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus())
                .contentType(MediaType.APPLICATION_JSON);

        // 2. 限流错误补上要等多少秒，其他错误按统一格式返回。
        if (exception.getRetryAfter() != null) {
            response.header("Retry-After", exception.getRetryAfter().toString());
        }
        return response.body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 返回字段校验说明，不回显密码等被拒绝的输入。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException exception) {
        // 只提取字段名和校验说明，不把密码等被拒绝的输入放进错误列表。
        List<Result.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new Result.FieldError(error.getField(), error.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(new Result<>("VALIDATION_ERROR", "参数校验失败", null, errors));
    }

    /**
     * 格式、类型或必填参数不符时统一返回 400。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestValueException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<Result<Void>> invalid(Exception exception) {
        return error(400, "VALIDATION_ERROR", "请求格式或参数不符合要求");
    }

    /**
     * 登录失效时返回 401，提示客户端重新登录。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> unauthenticated() {
        return error(401, "UNAUTHENTICATED", "请重新登录");
    }

    /**
     * 当前用户没有操作权限时返回 403。
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> forbidden() {
        return error(403, "FORBIDDEN", "没有操作权限");
    }

    /**
     * 唯一字段冲突时返回 409，不暴露数据库约束信息。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> unique() {
        return error(409, "UNIQUE_CONFLICT", "唯一字段已被使用");
    }

    /**
     * 数据库或事务不可用时返回 503，不伪装成成功结果。
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Result<Void>> unavailable() {
        return error(503, "SERVICE_UNAVAILABLE", "基础服务暂不可用");
    }

    /**
     * 请求路由不存在时返回统一的 404 响应。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> missing() {
        return error(404, "NOT_FOUND", "接口不存在");
    }

    /**
     * 请求方法不受支持时返回 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> method() {
        return error(405, "METHOD_NOT_ALLOWED", "请求方法不支持");
    }

    /**
     * 请求类型不受支持时返回 415。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> mediaType() {
        return error(415, "UNSUPPORTED_MEDIA_TYPE", "请求媒体类型不支持");
    }

    /**
     * 未预期异常只记录类型，响应不包含堆栈或秘密输入。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception exception) {
        // 日志只记异常类型，避免 SQL、平台或解析异常的消息夹带秘密输入。
        System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR,
                "未预期异常类型：" + exception.getClass().getName());
        return error(500, "INTERNAL_ERROR", "服务内部错误");
    }

    /**
     * 按指定 HTTP 状态组装统一的 JSON 错误响应。
     */
    private ResponseEntity<Result<Void>> error(int status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Result.error(code, message));
    }
}
