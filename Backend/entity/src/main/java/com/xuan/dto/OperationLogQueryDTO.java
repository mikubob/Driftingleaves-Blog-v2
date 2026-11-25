package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 操作日志只允许已声明筛选，类型与模块仅收ASCII标识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    @Builder.Default
    @Min(1)
    @Max(100000)
    private Integer page = 1;

    // 每页条数
    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    // 用户ID
    private String userId;

    // 目标ID
    private String targetId;

    // 操作类型
    @Pattern(regexp = "[A-Za-z0-9:_-]{1,32}", message = "只能包含字母、数字、冒号、下划线或连字符，最多32位")
    private String operationType;

    // 操作目标
    @Pattern(regexp = "[A-Za-z0-9:_-]{1,100}", message = "只能包含字母、数字、冒号、下划线或连字符，最多100位")
    private String operationTarget;

    // 操作结果
    @Min(0)
    @Max(1)
    private Integer result;

    // 开始时间
    private String begin;

    // 结束时间
    private String end;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
