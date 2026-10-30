package com.xuan.exception;

import lombok.Getter;

/** 这里的错误文案可以给用户看，转成 HTTP 响应由应用负责。 */
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
