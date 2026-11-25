package com.xuan.model;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 授权流程只存 Token 的关联摘要，保存多久由 Redis TTL 决定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthFlow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 发起授权的站点
    private String site;

    // 浏览器标识摘要
    private String browserHash;

    // 第三方平台来源
    private String source;

    // 操作类型
    private Operation operation;

    // 绑定目标用户ID
    private Long userId;

    // 原会话令牌摘要
    private String tokenHash;

    // 原会话认证时间
    private long authenticatedAt;

    // 流程创建时间
    private long createdAt;
}
