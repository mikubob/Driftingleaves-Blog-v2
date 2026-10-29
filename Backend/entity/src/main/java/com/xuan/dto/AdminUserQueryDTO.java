package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 用户列表过滤；角色包含已分配但禁用的关联，关键词按字面包含查询。 */
public record AdminUserQueryDTO(@Min(1) @Max(100000) Integer page,
                                @Min(1) @Max(100) Integer pageSize,
                                @TextLength(min = 1, max = 100) String keyword,
                                @Min(0) @Max(1) Integer status,
                                String roleId) {
    public AdminUserQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
    }
}
