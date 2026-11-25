package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 版本给 Controller 写响应头，不混进评论 JSON。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论信息
    private CommentAdminVO comment;

    // 资源版本号
    private String revision;
}
