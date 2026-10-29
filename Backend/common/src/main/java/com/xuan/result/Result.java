package com.xuan.result;

import java.util.List;

/** HTTP 业务结果；所有分支均保留 data 和 errors 字段。 */
public record Result<T>(String code, String msg, T data, List<FieldError> errors) {
    /** 创建成功结果，无返回值时传入 null。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", "成功", data, List.of());
    }

    /** 创建不包含内部异常信息的失败结果。 */
    public static Result<Void> error(String code, String message) {
        return new Result<>(code, message, null, List.of());
    }

    /** 字段错误仅描述约束，不回显输入值。 */
    public record FieldError(String field, String message) { }
}
