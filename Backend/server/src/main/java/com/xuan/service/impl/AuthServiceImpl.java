package com.xuan.service.impl;

import com.xuan.client.OAuthProviderClient;
import com.xuan.dto.AdminEmailCodeDTO;
import com.xuan.dto.EmailCodeDTO;
import com.xuan.dto.EmailLoginDTO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.OAuthAuthorizeDTO;
import com.xuan.dto.OAuthExchangeDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.exception.BusinessException;
import com.xuan.model.LoginSession;
import com.xuan.model.OAuthAuthorizeSession;
import com.xuan.ratelimit.AuthRateLimiter;
import com.xuan.security.WebSession;
import com.xuan.service.IAuthService;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.ProviderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证与第三方授权协调服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final IUserService userService;
    private final IOAuthService oauthService;
    private final OAuthProviderClient oauthProviderClient;
    private final SessionManager sessionManager;
    private final AuthRateLimiter authRateLimiter;
    private final CaptchaStore captchaStore;

    /**
     * 注册用户，先校验限流与图形验证码，再交给用户事务创建账号。
     *
     * @param body 注册参数
     * @param request 当前 HTTP 请求
     * @param browser OAuth 浏览器标识，可为空
     * @return 注册成功后的本人资料
     */
    @Override
    public MeVO register(RegisterDTO body, HttpServletRequest request, String browser) {
        // 1. 注册写库前先检查 IP 和浏览器对应的请求次数，并校验图形验证码。
        authRateLimiter.register(request, browser);
        verifyCaptcha(request);

        // 2. 交给用户业务事务创建账号，注册时不自动登录。
        return userService.register(body);
    }

    /**
     * 用户名密码登录，先校验限流与图形验证码，仅在凭据错误时累计失败次数。
     *
     * @param body 登录参数
     * @param side 登录端标识
     * @param request 当前 HTTP 请求
     * @return 登录会话结果
     */
    @Override
    public LoginSession login(LoginDTO body, WebSession side, HttpServletRequest request) {
        // 1. 先检查失败次数是否超限，再校验图形验证码。
        authRateLimiter.checkLogin(request, body.username());
        verifyCaptcha(request);

        // 2. 校验凭据并签发会话，仅凭据错误才计入密码输错次数。
        try {
            return userService.login(body, side.timeoutSeconds());
        } catch (BusinessException exception) {
            if ("AUTH_FAILED".equals(exception.getCode())) {
                authRateLimiter.loginFailed(request, body.username());
            }
            throw exception;
        }
    }

    /**
     * 发送后台登录邮箱验证码，先校验失败次数、图形验证码和发送频率。
     *
     * @param body 邮箱验证码请求参数
     * @param request 当前 HTTP 请求
     */
    @Override
    public void sendAdminEmailCode(AdminEmailCodeDTO body, HttpServletRequest request) {
        authRateLimiter.checkLogin(request, body.email());
        verifyCaptcha(request);
        authRateLimiter.email(request, body.email());

        try {
            userService.sendAdminEmailCode(body);
        } catch (BusinessException exception) {
            if ("AUTH_FAILED".equals(exception.getCode())) {
                authRateLimiter.loginFailed(request, body.email());
            }
            throw exception;
        }
    }

    /**
     * 发送博客端注册或登录邮箱验证码，先校验图形验证码和发送频率。
     *
     * @param body 邮箱验证码请求参数
     * @param request 当前 HTTP 请求
     */
    @Override
    public void sendBlogEmailCode(EmailCodeDTO body, HttpServletRequest request) {
        verifyCaptcha(request);
        authRateLimiter.email(request, body.email());
        userService.sendEmailCode(body);
    }

    /**
     * 邮箱验证码登录，先校验失败次数与图形验证码，凭据或验证码错误时累计失败次数。
     *
     * @param body 邮箱登录参数
     * @param side 登录端标识
     * @param request 当前 HTTP 请求
     * @return 登录会话结果
     */
    @Override
    public LoginSession emailLogin(EmailLoginDTO body, WebSession side, HttpServletRequest request) {
        authRateLimiter.checkLogin(request, body.email());
        verifyCaptcha(request);

        try {
            return userService.loginEmail(body, side.timeoutSeconds());
        } catch (BusinessException exception) {
            if ("AUTH_FAILED".equals(exception.getCode()) || "EMAIL_CODE_INVALID".equals(exception.getCode())) {
                authRateLimiter.loginFailed(request, body.email());
            }
            throw exception;
        }
    }

    /**
     * 撤销当前端登录会话。
     */
    @Override
    public void logout() {
        sessionManager.logout();
    }

    /**
     * 查询当前端可用的第三方登录平台，只返回平台标识和展示名称。
     *
     * @param side 登录端标识
     * @return 可用平台列表
     */
    @Override
    public List<ProviderVO> listProviders(WebSession side) {
        List<OAuthProviderClient.Provider> enabledProviders = oauthProviderClient.providers(side.site());
        List<ProviderVO> result = new ArrayList<>(enabledProviders.size());
        for (OAuthProviderClient.Provider provider : enabledProviders) {
            result.add(new ProviderVO(provider.source(), provider.displayName()));
        }
        return result;
    }

    /**
     * 发起第三方授权，绑定时先校验原会话与预期账号，再生成或复用浏览器标识并限流。
     *
     * @param side 登录端标识
     * @param source 平台标识
     * @param body 授权操作参数
     * @param request 当前 HTTP 请求
     * @param browser 已有浏览器标识，可为空
     * @return 授权地址信息及浏览器标识
     */
    @Override
    public OAuthAuthorizeSession authorize(WebSession side, String source, OAuthAuthorizeDTO body,
            HttpServletRequest request, String browser) {
        // 1. 绑定操作需要核对后台认证和页面预期账号。
        if (body.operation() == OAuthAuthorizeDTO.Operation.BIND) {
            if (side == WebSession.ADMIN) {
                sessionManager.requireAdminAuthentication();
            }
            WebSession.requireExpectedUser(request.getHeader(WebSession.EXPECTED_USER), sessionManager.currentUserId());
        }

        // 2. 复用合法浏览器标识，缺失或格式无效时重新生成。
        String browserId = browser != null && browser.matches("[A-Za-z0-9_-]{43}")
                ? browser
                : TokenUtils.random();

        // 3. 按登录或绑定操作分别限流，再生成第三方授权地址。
        if (body.operation() == OAuthAuthorizeDTO.Operation.LOGIN) {
            authRateLimiter.oauthLogin(request, browserId);
        } else {
            authRateLimiter.oauth(request, browserId);
        }

        OAuthAuthorizeVO authorizeVO = oauthService.authorize(side, source, body.operation(), browserId);
        return new OAuthAuthorizeSession(authorizeVO, browserId);
    }

    /**
     * 处理第三方授权回调并返回跳转地址。
     *
     * @param side 登录端标识
     * @param source 平台标识
     * @param state 授权流程 state
     * @param code 平台授权码
     * @param error 平台失败标记
     * @param browser 当前浏览器标识
     * @return 固定完成页跳转地址
     */
    @Override
    public String callback(WebSession side, String source, String state, String code, String error, String browser) {
        return oauthService.callback(side, source, state, code, error, browser);
    }

    /**
     * 兑换第三方授权一次性结果票据，先按 IP 和浏览器限流。
     *
     * @param side 登录端标识
     * @param body 兑换票据参数
     * @param request 当前 HTTP 请求
     * @param browser 当前浏览器标识
     * @return 登录会话结果或绑定结果
     */
    @Override
    public Object exchange(WebSession side, OAuthExchangeDTO body, HttpServletRequest request, String browser) {
        authRateLimiter.oauth(request, browser);
        String expectedUserId = request.getHeader(WebSession.EXPECTED_USER);
        return oauthService.exchange(side, body.ticket(), browser, expectedUserId);
    }

    /**
     * 从请求头读取并校验图形验证码。
     *
     * @param request 当前 HTTP 请求
     */
    private void verifyCaptcha(HttpServletRequest request) {
        captchaStore.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
    }
}
