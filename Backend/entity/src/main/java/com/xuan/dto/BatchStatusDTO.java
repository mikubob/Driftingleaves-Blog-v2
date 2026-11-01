package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 所有选中资源采用同一目标状态，1和2的业务含义由各入口决定。 */
@Getter
@Setter
public class BatchStatusDTO extends BatchRevisionDTO {
    @NotNull
    @Min(1)
    @Max(2)
    private Integer status;
}
