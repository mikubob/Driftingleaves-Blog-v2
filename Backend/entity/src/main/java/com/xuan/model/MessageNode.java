package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 留言关系数据，逐级查询回复时只读取所需字段，不提前加载隐藏的原文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言ID
    private Long messageId;

    // 留言用户ID
    private Long userId;

    // 父留言ID
    private Long parentId;

    // 所属会话根留言ID（根留言为 null）
    private Long rootId;

    // 审核状态
    private Integer status;

    // 是否悄悄话
    private Integer isSecret;

    // 创建时间
    private LocalDateTime createTime;
}
