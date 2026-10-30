package com.xuan.model;

import lombok.Data;
import java.time.LocalDateTime;

/** 公开逐层分页只读父链需要的轻量字段，不先加载隐藏原文。 */
@Data
public class MessageNode {
    private Long messageId;
    private Long userId;
    private Long parentId;
    private Integer status;
    private Integer isSecret;
    private LocalDateTime createTime;
}
