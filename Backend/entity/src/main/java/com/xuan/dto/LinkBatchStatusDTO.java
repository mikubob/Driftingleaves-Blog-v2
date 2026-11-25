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
 * 批量设置友链状态，0待审、1通过、2下架。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LinkBatchStatusDTO extends BatchRevisionDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    // 状态
    @NotNull
    @Min(0)
    @Max(2)
    private Integer status;
}
