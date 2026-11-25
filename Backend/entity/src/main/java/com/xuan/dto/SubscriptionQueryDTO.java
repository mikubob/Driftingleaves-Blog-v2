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
 * 邮箱仅供本人和ALL后台查看，后台按字面包含分页筛选。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionQueryDTO implements Serializable {

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

    // 电子邮箱
    @TextLength(min = 1, max = 254)
    private String email;

    // 用户ID
    private String userId;

    // 状态
    @Min(0)
    @Max(2)
    private Integer status;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }
}
