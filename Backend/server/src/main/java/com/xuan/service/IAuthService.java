package com.xuan.service;

import com.xuan.dto.AdminEmailCodeDTO;
import com.xuan.dto.EmailCodeDTO;
import com.xuan.dto.EmailLoginDTO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.OAuthAuthorizeDTO;
import com.xuan.dto.OAuthExchangeDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.model.LoginSession;
import com.xuan.model.OAuthAuthorizeSession;
import com.xuan.security.WebSession;
import com.xuan.vo.MeVO;
import com.xuan.vo.ProviderVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 认证与第三方授权协调服务接口
 */
public interface IAuthService {

    /**
     * 注册用户，先校验限流与图形验证码，再创建账号。
     *
     * @param body 注册参数
     * @param request 当前 HTTP 请求
     * @param browser OAuth 浏览器标识，可为空
     * @return 注册成功后的本人资料
     */
    MeVO register(RegisterDTO body, HttpServletRequest request, String browser);

    /**
     * 用户名密码登录，先校验限流与图形验证码，密码错误时累计失败次数。
     *
     * @param body 登录参数
     * @param side 登录端标识
     * @param request 当前 HTTP 请求
     * @return 登录会话结果
     */
    LoginSession login(LoginDTO body, WebSession side, HttpServletRequest request);

    /**
     * 发送后台登录邮箱验证码
     *
     * @param body 邮箱验证码请求参数
     * @param request 当前 HTTP 请求
     */
    void sendAdminEmailCode(AdminEmailCodeDTO body, HttpServletRequest request);

    /**
     * 发送博客端注册或登录邮箱验证码
     *
     * @param body 邮箱验证码请求参数
     * @param request 当前 HTTP 请求
     */
    void sendBlogEmailCode(EmailCodeDTO body, HttpServletRequest request);

    /**
     * 邮箱验证码登录，先校验限流与图形验证码，认证失败时累计失败次数。
     *
     * @param body 邮箱登录参数
     * @param side 登录端标识
     * @param request 当前 HTTP 请求
     * @return 登录会话结果
     */
    LoginSession emailLogin(EmailLoginDTO body, WebSession side, HttpServletRequest request);

    /**
     * 撤销当前端登录会话
     */
    void logout();

    /**
     * 查询当前端可用的第三方登录平台列表
     *
     * @param side 登录端标识
     * @return 可用平台列表
     */
    List<ProviderVO> listProviders(WebSession side);

    /**
     * 发起第三方授权，绑定操作校验原会话与预期账号，并生成或复用浏览器标识。
     *
     * @param side 登录端标识
     * @param source 平台标识
     * @param body 授权操作参数
     * @param request 当前 HTTP 请求
     * @param browser 已有浏览器标识，可为空
     * @return 授权地址信息及浏览器标识
     */
    OAuthAuthorizeSession authorize(WebSession side, String source, OAuthAuthorizeDTO body,
            HttpServletRequest request, String browser);

    /**
     * 处理第三方授权回调并返回跳转地址
     *
     * @param side 登录端标识
     * @param source 平台标识
     * @param state 授权流程 state
     * @param code 平台授权码
     * @param error 平台失败标记
     * @param browser 当前浏览器标识
     * @return 固定完成页跳转地址
     */
    String callback(WebSession side, String source, String state, String code, String error, String browser);

    /**
     * 兑换第三方授权一次性结果票据
     *
     * @param side 登录端标识
     * @param body 兑换票据参数
     * @param request 当前 HTTP 请求
     * @param browser 当前浏览器标识
     * @return 登录会话结果或绑定结果
     */
    Object exchange(WebSession side, OAuthExchangeDTO body, HttpServletRequest request, String browser);
}
