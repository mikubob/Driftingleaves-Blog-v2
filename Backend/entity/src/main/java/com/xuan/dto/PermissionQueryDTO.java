package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** 权限字典分页，编码或名称字面包含，模块与状态精确过滤。 */
public record PermissionQueryDTO(@Min(1) @Max(100000) Integer page,
                                 @Min(1) @Max(100) Integer pageSize,
                                 @TextLength(min = 1, max = 100) String keyword,
                                 @Pattern(regexp = "[a-z][a-z0-9_]{0,31}") String module,
                                 @Min(0) @Max(1) Integer status) {
    public PermissionQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        keyword = keyword == null ? null : keyword.strip();
        module = module == null ? null : module.strip();
    }
}
