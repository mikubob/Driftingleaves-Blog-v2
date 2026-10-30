package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 邮箱仅供本人和ALL后台查看，后台按字面包含分页筛选。 */
public record SubscriptionQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(100) Integer pageSize,
        @TextLength(min = 1, max = 254) String email, String userId, @Min(0) @Max(2) Integer status) {
    public SubscriptionQueryDTO { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; email = email == null ? null : email.strip(); }
}
