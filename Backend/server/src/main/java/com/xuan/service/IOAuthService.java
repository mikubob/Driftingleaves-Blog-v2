package com.xuan.service;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.security.WebSession;
import com.xuan.vo.OAuthAuthorizeVO;

/**
 * 第三方授权业务接口，一次性流程记录浏览器、操作、平台、目标用户和原 Token。
 */
public interface IOAuthService {

    /**
     * 生成第三方授权地址，记录浏览器、平台和本次操作，绑定账号时还要检查原会话。
     */
    OAuthAuthorizeVO authorize(WebSession side, String source, Operation operation, String browser);

    /** 处理第三方授权回调，核对并一次性使用授权流程后，返回固定跳转地址；流程信息不可信时直接返回 HTTP 错误。 */
    String callback(WebSession side, String source, String state, String code, String error, String browser);

    /** 在发起端兑换授权结果，登录签发本端会话，绑定必须携带原 Token。 */
    Object exchange(WebSession side, String ticket, String browser, String expectedUserId);
}
