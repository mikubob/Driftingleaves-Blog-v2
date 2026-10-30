package com.xuan.model;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;

/**
 * 授权流程只存 Token 的关联摘要，保存多久由 Redis TTL 决定。
 */
public record OAuthFlow(String browserHash, String source, Operation operation, Long userId,
                        String tokenHash, long authenticatedAt, long createdAt) {

}
