package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作者发现页只返回公开资料和当前公开文章的汇总。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户ID
    private Long userId;

    // 昵称
    private String nickname;

    // 头像URL
    private String avatar;

    // 个人简介
    private String intro;

    // 文章数
    private String articleCount;

    // 最近发布时间
    private LocalDateTime lastPublishTime;
}
