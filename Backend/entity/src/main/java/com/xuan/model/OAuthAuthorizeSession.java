package com.xuan.model;

import com.xuan.vo.OAuthAuthorizeVO;

/**
 * 第三方授权发起结果，包含授权地址信息及浏览器标识。
 */
public record OAuthAuthorizeSession(OAuthAuthorizeVO authorize, String browserId) {
}
