package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户状态和同一次读取时的真实点赞明细数量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeStateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 是否已点赞
    private boolean liked;

    // 点赞数
    private Long likeCount;
}
