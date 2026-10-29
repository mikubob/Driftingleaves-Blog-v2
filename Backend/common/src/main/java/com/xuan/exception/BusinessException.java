package com.xuan.exception;

import lombok.Getter;

/** 可安全对外显示的业务失败；HTTP 转换由应用模块负责。 */
@Getter
public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;
    private final Integer retryAfter;

    public BusinessException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(int status, String code, String message, Integer retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }
}
