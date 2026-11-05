package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 查询整组对话，未传页码时定位当前记录所在的回复页。 */
public record DiscussionConversationQueryDTO(@Min(1) @Max(100000) Integer page,
                                             @Min(1) @Max(50) Integer pageSize) {

    public DiscussionConversationQueryDTO {
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
