package com.xuan.handle;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 统一处理接口异常，不返回用户输入、SQL、第三方响应或堆栈。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Pattern UNIQUE_KEY = Pattern.compile("for key ['`](?:[^'`]+\\.)?([^'`]+)['`]\\s*$");

    /** 处理上传文件过大，返回 413，不返回临时目录。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> uploadTooLarge() {
        return error(413, "PAYLOAD_TOO_LARGE", "上传内容过大：图片限 10 MiB、音频限 60 MiB、歌词限 1 MiB。请缩小文件后重试。");
    }

    /** 处理上传格式错误，缺少文件等问题按请求错误返回。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> multipart() {
        return error(400, "VALIDATION_ERROR", "未能读取上传文件，请重新选择文件后上传。");
    }

    /**
     * 保留业务错误状态、错误码和字段说明，限流时补充 Retry-After。
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
        return response.body(new Result<>(exception.getCode(), exception.getMessage(), null, exception.getErrors()));
    }

    /**
     * 返回字段校验说明，不回显密码等被拒绝的输入。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException exception) {
        // 只提取字段名和校验说明，不把密码等被拒绝的输入放进错误列表。
        List<Result.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new Result.FieldError(error.getField(),
                        RequestMessages.name(error.getField()) + "：" + error.getDefaultMessage())).toList();
        return validationErrors(errors);
    }

    /**
     * 格式、类型或必填参数不符时统一返回 400。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestValueException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<Result<Void>> invalid(Exception exception) {
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            return validationErrors(List.of(new Result.FieldError(mismatch.getName(),
                    RequestMessages.name(mismatch.getName()) + "格式不正确，请按页面要求填写。")));
        }
        if (exception instanceof MissingServletRequestParameterException missing) {
            return validationErrors(List.of(new Result.FieldError(missing.getParameterName(),
                    "请填写" + RequestMessages.name(missing.getParameterName()) + "。")));
        }
        if (exception instanceof MissingRequestHeaderException missing) {
            return error(400, "VALIDATION_ERROR", "缺少" + RequestMessages.name(missing.getHeaderName())
                    + "，请刷新页面后重试。");
        }
        if (exception instanceof ConstraintViolationException violations) {
            List<Result.FieldError> errors = violations.getConstraintViolations().stream()
                    .map(violation -> new Result.FieldError(violation.getPropertyPath().toString(),
                            RequestMessages.name(violation.getPropertyPath().toString()) + "：" + violation.getMessage()))
                    .toList();
            return validationErrors(errors);
        }
        if (exception instanceof HandlerMethodValidationException validation) {
            List<Result.FieldError> errors = new ArrayList<>();
            for (var parameter : validation.getParameterValidationResults()) {
                String field = parameter.getMethodParameter().getParameterName();
                for (var detail : parameter.getResolvableErrors()) {
                    errors.add(new Result.FieldError(field, RequestMessages.name(field) + "：" + detail.getDefaultMessage()));
                }
            }
            return validationErrors(errors);
        }
        if (exception instanceof HttpMessageNotReadableException) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof UnrecognizedPropertyException) {
                    return error(400, "VALIDATION_ERROR", "提交内容包含当前不支持的项目，请刷新页面后重新填写。");
                }
                if (cause instanceof JacksonException json && !json.getPath().isEmpty()) {
                    String field = "request";
                    for (var reference : json.getPath()) {
                        if (reference.getPropertyName() != null) {
                            field = reference.getPropertyName();
                        }
                    }
                    return validationErrors(List.of(new Result.FieldError(field,
                            RequestMessages.name(field) + "格式不正确，请按页面要求重新填写。")));
                }
            }
        }
        return error(400, "VALIDATION_ERROR", "填写内容的格式有误或缺少必填项，请检查后重新提交；仍失败时请刷新页面重试。");
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
    public ResponseEntity<Result<Void>> unique(DuplicateKeyException exception) {
        // 驱动可能在SQLException下继续包装原因，不能只检查最深层异常。
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof SQLException sql) || sql.getErrorCode() != 1062 || sql.getMessage() == null) {
                continue;
            }
            Matcher match = UNIQUE_KEY.matcher(sql.getMessage());
            if (!match.find()) {
                continue;
            }
            Result.FieldError field = duplicateField(match.group(1));
            if (field != null) {
                return ResponseEntity.status(409).contentType(MediaType.APPLICATION_JSON)
                        .body(new Result<>("UNIQUE_CONFLICT", field.message(), null, List.of(field)));
            }
        }
        return error(409, "UNIQUE_CONFLICT", "该记录已存在，请刷新页面；如需修改，请编辑已有记录。");
    }

    /** 将数据库已声明的唯一键转换成具体字段提示，不返回SQL和冲突值。 */
    private Result.FieldError duplicateField(String key) {
        return switch (key) {
            case "uk_article_slug" -> new Result.FieldError("slug", "URL 标识已存在，不允许重复，请修改后重试。");
            case "uk_category_name" -> new Result.FieldError("categoryName", "分类名称已存在，不允许重复，请修改后重试。");
            case "uk_category_slug" -> new Result.FieldError("slug", "分类 URL 标识已存在，不允许重复，请修改后重试。");
            case "uk_tag_name" -> new Result.FieldError("tagName", "标签名称已存在，不允许重复，请修改后重试。");
            case "uk_tag_slug" -> new Result.FieldError("slug", "标签 URL 标识已存在，不允许重复，请修改后重试。");
            case "uk_user_username" -> new Result.FieldError("username", "用户名已被使用，请更换用户名。");
            case "uk_user_email" -> new Result.FieldError("email", "邮箱已被使用，请更换邮箱。");
            case "uk_role_code" -> new Result.FieldError("roleCode", "角色编码已存在，不允许重复，请修改后重试。");
            case "uk_permission_code" -> new Result.FieldError("permissionCode", "权限编码已存在，不允许重复，请修改后重试。");
            case "uk_menu_code" -> new Result.FieldError("menuCode", "菜单编码已存在，不允许重复，请修改后重试。");
            case "uk_menu_route_name" -> new Result.FieldError("routeName", "路由名称已存在，不允许重复，请修改后重试。");
            case "uk_menu_path" -> new Result.FieldError("path", "路由路径已存在，不允许重复，请修改后重试。");
            case "uk_oauth_identity" -> new Result.FieldError("source", "该第三方账号已绑定其他账号，请更换第三方账号。");
            case "uk_oauth_user_source" -> new Result.FieldError("source", "当前账号已绑定该平台，请先解除已有绑定。");
            case "uk_rss_user" -> new Result.FieldError("userId", "当前账号已有订阅，请刷新页面后修改已有订阅。");
            case "uk_rss_email" -> new Result.FieldError("email", "订阅邮箱已被使用，请更换邮箱。");
            case "uk_file_storage" -> new Result.FieldError("file", "文件存储记录已存在，请重新选择文件上传。");
            case "uk_visitor_fingerprint" -> new Result.FieldError("fingerprint", "访客记录已存在，请刷新页面后重试。");
            case "uk_comment_article" -> new Result.FieldError("articleId", "这条文章评论已存在，请刷新评论列表后重试。");
            default -> null;
        };
    }

    /** 将字段说明直接组成可展示的提示，各前端不需要再拼接英文字段名。 */
    private ResponseEntity<Result<Void>> validationErrors(List<Result.FieldError> errors) {
        String message = errors.stream().map(Result.FieldError::message).distinct().collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "请检查必填项和填写格式后重新提交。";
        }
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(new Result<>("VALIDATION_ERROR", message, null, errors));
    }

    /**
     * 数据库或事务不可用时返回 503，不伪装成成功结果。
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Result<Void>> unavailable(Exception exception) {
        // 只记录诊断码和类型，异常消息可能包含 SQL、参数或连接凭据。
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        if (cause instanceof SQLException sql) {
            log.error("基础服务异常：类型={}，根因类型={}，SQLState={}，数据库错误码={}",
                    exception.getClass().getName(), cause.getClass().getName(), sql.getSQLState(), sql.getErrorCode());
        } else {
            log.error("基础服务异常：类型={}，根因类型={}",
                    exception.getClass().getName(), cause.getClass().getName());
        }
        return error(503, "SERVICE_UNAVAILABLE", "服务暂时不可用，请保留当前输入并稍后重试；提交结果可在列表中确认。");
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
        return error(500, "INTERNAL_ERROR", "暂时无法完成操作，请保留当前输入并稍后重试；仍失败时请联系站点管理员。");
    }

    /**
     * 按指定 HTTP 状态组装统一的 JSON 错误响应。
     */
    private ResponseEntity<Result<Void>> error(int status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Result.error(code, message));
    }
}
