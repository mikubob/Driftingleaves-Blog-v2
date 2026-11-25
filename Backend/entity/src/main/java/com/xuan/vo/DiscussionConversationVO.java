package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 返回主记录和当前页回复，回复总数不包含主记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionConversationVO<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 根记录
    private T root;

    // 所在页码
    private int page;

    // 每页条数
    private int pageSize;

    // 总数
    private String total;

    // 总页数
    private String totalPages;

    // 当前页记录列表
    private List<T> records;
}
