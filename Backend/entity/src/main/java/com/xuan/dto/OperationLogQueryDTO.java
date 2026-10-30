package com.xuan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

/** 操作日志只允许已声明筛选，类型与模块仅收ASCII标识。 */
public record OperationLogQueryDTO(@Min(1) @Max(100000) Integer page,
        @Min(1) @Max(100) Integer pageSize, String userId, String targetId,
        @Pattern(regexp = "[A-Za-z0-9:_-]{1,32}") String operationType,
        @Pattern(regexp = "[A-Za-z0-9:_-]{1,100}") String operationTarget,
        @Min(0) @Max(1) Integer result, String begin, String end) {
    public OperationLogQueryDTO { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
}
