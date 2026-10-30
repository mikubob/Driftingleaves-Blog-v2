package com.xuan.model;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.vo.OAuthBindingVO;

/** 授权结果只存验证过的身份和绑定资料，不存平台 Token 或 code。 */
public record OAuthTicket(String browserHash, String source, Operation operation, Long userId,
                          String tokenHash, long authenticatedAt, OAuthBindingVO binding, String uuid) { }
