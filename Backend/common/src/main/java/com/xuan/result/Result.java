package com.xuan.result;

import java.util.List;

/**
 * 统一接口响应，成功和失败都返回 data、errors 字段。
 */
public record Result<T>(String code, String msg, T data, List<FieldError> errors) {

    /** 生成成功响应，没有业务结果就传 null。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", "成功", data, List.of());
    }

    /** 生成失败响应，不带内部异常详情。 */
    public static Result<Void> error(String code, String message) {
        return new Result<>(code, message, null, List.of());
    }

    /**
     * 字段错误说明，只返回字段名和原因，不返回用户输入。
     */
    public record FieldError(String field, String message) {

    }
}
