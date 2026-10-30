package com.xuan.service;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.vo.OAuthAuthorizeVO;

/**
 * OAuth 一次性流程要关联浏览器、操作、平台、目标用户和原 Token。
 */
public interface IOAuthService {

    /**
     * 关联浏览器、平台和本次操作后生成授权地址，绑定还要验证原会话。
     */
    OAuthAuthorizeVO authorize(String source, Operation operation, String browser);

    /** 可信且已消费的流程返回固定跳转地址，关系不可信就直接报 HTTP 错误。 */
    String callback(String source, String state, String code, String error, String browser);

    /** 登录签发 Session，绑定必须带原 Token 且只返回绑定结果。 */
    Object exchange(String ticket, String browser);
}
