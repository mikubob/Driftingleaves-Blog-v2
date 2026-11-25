package com.xuan.constant;

/**
 * 统一接口响应与业务异常错误码常量，避免在业务层和异常处理器中散落魔法字符串。
 */
public final class ErrorCode {

    public static final String OK = "OK";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String ACCESS_BLOCKED = "ACCESS_BLOCKED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String UNIQUE_CONFLICT = "UNIQUE_CONFLICT";
    public static final String STATE_CONFLICT = "STATE_CONFLICT";
    public static final String REVISION_CONFLICT = "REVISION_CONFLICT";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String PRECONDITION_REQUIRED = "PRECONDITION_REQUIRED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private ErrorCode() {
    }
}
