package com.xuan.model;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;

/** 授权流程传输模型只保存 Token 关联摘要；创建时间由 Redis TTL 约束。 */
public record OAuthFlow(String browserHash, String source, Operation operation, Long userId,
                        String tokenHash, long authenticatedAt, long createdAt) { }
