package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 返回可见回复对象的昵称和原文摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionReplyTargetVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 昵称
    private String nickname;

    // 内容
    private String content;
}
