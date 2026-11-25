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
 * 创建经历，在资料字段之外指定初始状态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExperienceCreateDTO extends ExperienceWriteDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    // 状态
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
