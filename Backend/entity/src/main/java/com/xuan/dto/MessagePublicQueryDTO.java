package com.xuan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 前台每次展开一层，总数只计当前会话可见的直接子节点。 */
public record MessagePublicQueryDTO(@Min(1) @Max(100000) Integer page, @Min(1) @Max(50) Integer pageSize, String parentId) {
    public MessagePublicQueryDTO { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
}
