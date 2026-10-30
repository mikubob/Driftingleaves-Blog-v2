package com.xuan.result;

import java.util.List;

/** 成功或失败都带上 data 和 errors，前端不用猜字段有没有。 */
public record Result<T>(String code, String msg, T data, List<FieldError> errors) {
    /** 生成成功响应，没有业务结果就传 null。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", "成功", data, List.of());
    }

    /** 生成失败响应，不带内部异常详情。 */
    public static Result<Void> error(String code, String message) {
        return new Result<>(code, message, null, List.of());
    }

    /** 这里只写哪个字段不合要求，不回显用户输入。 */
    public record FieldError(String field, String message) { }
}
