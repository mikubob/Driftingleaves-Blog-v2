package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 批量设置友链状态，0待审、1通过、2下架。 */
@Getter
@Setter
public class LinkBatchStatusDTO extends BatchRevisionDTO {

    @NotNull
    @Min(0)
    @Max(2)
    private Integer status;
}
