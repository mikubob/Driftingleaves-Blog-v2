package com.xuan.vo;

/**
 * 返回平台授权地址和 state 还能用多少秒。
 */
public record OAuthAuthorizeVO(String authorizationUrl, int expiresIn) {

}
