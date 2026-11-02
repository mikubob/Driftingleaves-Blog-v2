package com.xuan.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 图片压缩只影响新上传，保留 PNG/JPEG 格式，目标大小不作为上传拒绝条件。 */
@Data
@Validated
@ConfigurationProperties("app.image.compress")
public class ImageProperties {

    private boolean enabled = true;

    /** 压缩目标，单位 KiB；达到最低质量后仍可能超过此值。 */
    @Min(1)
    @Max(10240)
    private int maxSizeKb = 500;

    /** JPEG 起始质量，最低降到 0.3；PNG 继续使用无损编码。 */
    @DecimalMin("0.3")
    @DecimalMax("1.0")
    private double quality = 0.9;

    /** 超出此最长边时等比缩小，不放大小图。 */
    @Min(1)
    @Max(10000)
    private int maxDimension = 2560;
}
