package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * 所有选中资源采用同一目标状态，1和2的业务含义由各入口决定。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchStatusDTO extends BatchRevisionDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    // 状态
    @NotNull
    @Min(1)
    @Max(2)
    private Integer status;
}
