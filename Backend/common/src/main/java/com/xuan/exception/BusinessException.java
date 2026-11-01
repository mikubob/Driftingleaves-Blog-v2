package com.xuan.exception;

import com.xuan.result.Result;
import lombok.Getter;

import java.util.List;

/**
 * 这里的错误文案可以给用户看，转成 HTTP 响应由应用负责。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int status;
    private final String code;
    private final Integer retryAfter;
    private final List<Result.FieldError> errors;

    public BusinessException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(int status, String code, String message, Integer retryAfter) {
        this(status, code, message, retryAfter, List.of());
    }

    /** 字段冲突只带字段名和说明，不回显用户输入或数据库信息。 */
    public BusinessException(int status, String code, String message, Integer retryAfter,
            List<Result.FieldError> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
        this.errors = List.copyOf(errors);
    }
}
