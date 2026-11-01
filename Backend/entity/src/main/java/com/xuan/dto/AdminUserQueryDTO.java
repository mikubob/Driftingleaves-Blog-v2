package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 按字面关键词筛选用户；按角色查找时，已经分配但被禁用的角色也算关联。
 */
public record AdminUserQueryDTO(@Min(1) @Max(100000) Integer page,
                                @Min(1) @Max(100) Integer pageSize,
                                @TextLength(min = 1, max = 100) String keyword,
                                @Min(0) @Max(1) Integer status,
                                String roleId) {

    public AdminUserQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        keyword = keyword == null ? null : keyword.strip();
    }
}
