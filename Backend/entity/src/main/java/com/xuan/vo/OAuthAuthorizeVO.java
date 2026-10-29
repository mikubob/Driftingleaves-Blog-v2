package com.xuan.vo;



/** 平台授权入口和 state 有效秒数。 */
public record OAuthAuthorizeVO(String authorizationUrl, int expiresIn) { }
