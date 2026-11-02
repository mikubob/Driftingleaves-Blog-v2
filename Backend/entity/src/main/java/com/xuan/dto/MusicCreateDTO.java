package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 创建音乐，在资料字段之外指定初始状态。 */
@Getter
@Setter
public class MusicCreateDTO extends MusicWriteDTO {

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
