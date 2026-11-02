package com.xuan.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留言关系数据，逐级查询回复时只读取所需字段，不提前加载隐藏的原文。
 */
@Data
public class MessageNode {

    private Long messageId;
    private Long userId;
    private Long parentId;
    private Integer status;
    private Integer isSecret;
    private LocalDateTime createTime;
}
