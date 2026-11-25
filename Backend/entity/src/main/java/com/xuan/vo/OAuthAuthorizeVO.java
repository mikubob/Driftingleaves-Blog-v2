package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 返回平台授权地址和 state 还能用多少秒。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 第三方授权跳转地址
    private String authorizationUrl;

    // 过期时间（秒）
    private int expiresIn;
}
