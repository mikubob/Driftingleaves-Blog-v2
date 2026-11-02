package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 批量设置0或1状态，单个目标也提交一项。 */
@Getter
@Setter
public class BatchBinaryStatusDTO extends BatchRevisionDTO {

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
