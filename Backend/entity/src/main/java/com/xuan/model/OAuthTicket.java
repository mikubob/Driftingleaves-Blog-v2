package com.xuan.model;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.vo.OAuthBindingVO;

/** 授权结果票据只含已验证身份与绑定投影，不保存平台 Token/code。 */
public record OAuthTicket(String browserHash, String source, Operation operation, Long userId,
                          String tokenHash, long authenticatedAt, OAuthBindingVO binding, String uuid) { }
