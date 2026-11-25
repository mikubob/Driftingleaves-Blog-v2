package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询整组对话，未传页码时定位当前记录所在的回复页。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionConversationQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    @Min(1)
    @Max(100000)
    private Integer page;

    // 每页条数
    @Builder.Default
    @Min(1)
    @Max(50)
    private Integer pageSize = 20;

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize == null ? 20 : pageSize;
    }
}
