package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 访客原始清单仅向ALL后台提供，不接受任意排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    @Builder.Default
    @Min(1)
    @Max(100000)
    private Integer page = 1;

    // 每页条数
    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    // 国家
    @TextLength(min = 1, max = 64)
    private String country;

    // 省份
    @TextLength(min = 1, max = 64)
    private String province;

    // 城市
    @TextLength(min = 1, max = 64)
    private String city;

    // 是否拉黑
    @Min(0)
    @Max(1)
    private Integer isBlocked;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
