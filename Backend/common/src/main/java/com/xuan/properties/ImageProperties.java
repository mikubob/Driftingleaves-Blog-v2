package com.xuan.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 图片压缩与转码配置，开启后将上传图片统一压缩并转码为指定输出格式。
 */
@Data
@Validated
@ConfigurationProperties("app.image.compress")
public class ImageProperties {

    private boolean enabled = true;

    /**
     * 压缩目标，单位 KiB；达到最低质量后仍可能超过此值。
     */
    @Min(1)
    @Max(10240)
    private int maxSizeKb = 500;

    /**
     * 起始压缩质量，最低降到 0.3。
     */
    @DecimalMin("0.3")
    @DecimalMax("1.0")
    private double quality = 0.9;

    /**
     * 超出此最长边时等比缩小，不放大小图。
     */
    @Min(1)
    @Max(10000)
    private int maxDimension = 2560;

    /**
     * 目标输出格式，默认统一转码为 webp。
     */
    @Pattern(regexp = "(?i)webp|png|jpeg", message = "图片输出格式仅支持 webp、png 或 jpeg")
    private String outputFormat = "webp";
}
