package com.xuan.exception;

import com.xuan.result.Result;
import lombok.Getter;

import java.util.List;

/**
 * 业务异常，文案可以展示给用户，HTTP 响应由应用层转换。
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

    /** 创建字段冲突异常，只保存字段名和说明，不带用户输入或数据库信息。 */
    public BusinessException(int status, String code, String message, Integer retryAfter,
            List<Result.FieldError> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
        this.errors = List.copyOf(errors);
    }
}
