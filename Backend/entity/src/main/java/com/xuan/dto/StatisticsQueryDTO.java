package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * UTC自然日区间由业务层成对检查，排行入口仅接收limit而不接收siteCode。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 开始时间
    private String begin;

    // 结束时间
    private String end;

    // 站点编码
    @Pattern(regexp = "home|blog|cv", message = "请选择主页、博客或简历站点")
    private String siteCode;

    // 返回条数上限
    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer limit = 10;

    public void setLimit(Integer limit) {
        this.limit = ObjectUtil.defaultIfNull(limit, 10);
    }
}
