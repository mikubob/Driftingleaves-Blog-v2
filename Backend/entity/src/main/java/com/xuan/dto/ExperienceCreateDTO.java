package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 创建经历，在资料字段之外指定初始状态。 */
@Getter
@Setter
public class ExperienceCreateDTO extends ExperienceWriteDTO {

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
