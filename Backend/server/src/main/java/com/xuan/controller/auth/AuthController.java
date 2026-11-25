package com.xuan.controller.auth;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.AdminEmailCodeDTO;
import com.xuan.dto.EmailCodeDTO;
import com.xuan.dto.EmailLoginDTO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.OAuthAuthorizeDTO;
import com.xuan.dto.OAuthExchangeDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.model.LoginSession;
import com.xuan.model.OAuthAuthorizeSession;
import com.xuan.result.Result;
import com.xuan.security.WebSession;
import com.xuan.service.IAuthService;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.OAuthLoginResultVO;
import com.xuan.vo.ProviderVO;
import com.xuan.vo.SessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * 认证与第三方授权接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    /**
     * 第三方授权浏览器标识 Cookie，不作为登录凭据。
     */
    private static final String BROWSER_COOKIE = "__Host-dl-oauth-browser";

    private final IAuthService authService;

    /**
     * 用户注册
     *
     * @param body 注册参数
     * @param request 当前 HTTP 请求
     * @param browser OAuth 浏览器标识，可为空
     * @return 201 响应及本人资料
     */
    @PostMapping({"/admin/auth/register", "/blog/auth/register"})
    public ResponseEntity<Result<MeVO>> register(@Valid @RequestBody RegisterDTO body,
            HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        log.info("用户注册: username={}", body.username());
        MeVO me = authService.register(body, request, browser);
        return ResponseEntity.status(201).body(Result.ok(me));
    }

    /**
     * 用户名密码登录
     *
     * @param body 登录参数
     * @param request 当前 HTTP 请求
     * @return 登录 Cookie 与会话资料
     */
    @PostMapping({"/admin/auth/login", "/blog/auth/login"})
    @RateLimit(value = "login-total", limit = 30)
    public ResponseEntity<Result<SessionVO>> login(@Valid @RequestBody LoginDTO body,
            HttpServletRequest request) {
        log.info("用户名密码登录: username={}", body.username());
        WebSession side = WebSession.forPath(request.getServletPath());
        LoginSession session = authService.login(body, side, request);
        return side.login(session);
    }

    /**
     * 发送后台登录邮箱验证码
     *
     * @param body 邮箱验证码请求参数
     * @param request 当前 HTTP 请求
     * @return 成功结果
     */
    @PostMapping("/admin/auth/email-code")
    @RateLimit(value = "login-total", limit = 30)
    public Result<Void> adminEmailCode(@Valid @RequestBody AdminEmailCodeDTO body,
            HttpServletRequest request) {
        log.info("发送后台登录邮箱验证码");
        authService.sendAdminEmailCode(body, request);
        return Result.ok(null);
    }

    /**
     * 发送博客端注册或登录邮箱验证码
     *
     * @param body 邮箱验证码请求参数
     * @param request 当前 HTTP 请求
     * @return 成功结果
     */
    @PostMapping("/blog/auth/email-code")
    public Result<Void> blogEmailCode(@Valid @RequestBody EmailCodeDTO body,
            HttpServletRequest request) {
        log.info("发送博客端邮箱验证码: purpose={}", body.purpose());
        authService.sendBlogEmailCode(body, request);
        return Result.ok(null);
    }

    /**
     * 邮箱验证码登录
     *
     * @param body 邮箱登录参数
     * @param request 当前 HTTP 请求
     * @return 登录 Cookie 与会话资料
     */
    @PostMapping({"/admin/auth/email-login", "/blog/auth/email-login"})
    @RateLimit(value = "login-total", limit = 30)
    public ResponseEntity<Result<SessionVO>> emailLogin(@Valid @RequestBody EmailLoginDTO body,
            HttpServletRequest request) {
        log.info("邮箱验证码登录");
        WebSession side = WebSession.forPath(request.getServletPath());
        LoginSession session = authService.emailLogin(body, side, request);
        return side.login(session);
    }

    /**
     * 退出登录，撤销当前端会话并清除登录 Cookie。
     *
     * @param request 当前 HTTP 请求
     * @return 成功结果
     */
    @PostMapping({"/admin/auth/logout", "/blog/auth/logout"})
    public ResponseEntity<Result<Void>> logout(HttpServletRequest request) {
        log.info("用户退出登录");
        WebSession side = WebSession.forPath(request.getServletPath());
        authService.logout();
        return side.clearLoginCookie();
    }

    /**
     * 查询可用的第三方登录平台
     *
     * @param request 当前 HTTP 请求
     * @return 可用平台列表
     */
    @GetMapping({"/admin/auth/oauth/providers", "/blog/auth/oauth/providers"})
    public Result<List<ProviderVO>> providers(HttpServletRequest request) {
        log.info("查询可用的第三方登录平台");
        WebSession side = WebSession.forPath(request.getServletPath());
        List<ProviderVO> providerList = authService.listProviders(side);
        return Result.ok(providerList);
    }

    /**
     * 发起第三方授权并写入浏览器流程 Cookie
     *
     * @param source 平台标识
     * @param body 授权操作参数
     * @param request 当前 HTTP 请求
     * @param browser 已有浏览器标识，可为空
     * @return 授权地址、有效期及 Cookie 响应头
     */
    @PostMapping({"/admin/auth/oauth/{source}/authorize", "/blog/auth/oauth/{source}/authorize"})
    public ResponseEntity<Result<OAuthAuthorizeVO>> authorize(@PathVariable String source,
            @Valid @RequestBody OAuthAuthorizeDTO body,
            HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        log.info("发起第三方授权: source={}, operation={}", source, body.operation());
        WebSession side = WebSession.forPath(request.getServletPath());
        OAuthAuthorizeSession authorizeSession = authService.authorize(side, source, body, request, browser);
        ResponseCookie cookie = ResponseCookie.from(BROWSER_COOKIE, authorizeSession.browserId())
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(600))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Result.ok(authorizeSession.authorize()));
    }

    /**
     * 处理第三方授权回调并重定向到固定完成页
     *
     * @param source 平台标识
     * @param state 授权流程 state
     * @param code 平台授权码
     * @param error 平台失败标记
     * @param request 当前 HTTP 请求
     * @param browser 当前浏览器标识
     * @return 303 重定向响应
     */
    @GetMapping({"/admin/auth/oauth/{source}/callback", "/blog/auth/oauth/{source}/callback"})
    @RateLimit(value = "oauth-callback", limit = 30)
    public ResponseEntity<Void> callback(@PathVariable String source,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        log.info("处理第三方授权回调: source={}", source);
        WebSession side = WebSession.forPath(request.getServletPath());
        String redirect = authService.callback(side, source, state, code, error, browser);
        return ResponseEntity.status(303).location(URI.create(redirect)).build();
    }

    /**
     * 兑换第三方授权一次性结果票据
     *
     * @param body 票据参数
     * @param request 当前 HTTP 请求
     * @param browser 当前浏览器标识
     * @return 登录会话或绑定结果
     */
    @PostMapping({"/admin/auth/oauth/exchange", "/blog/auth/oauth/exchange"})
    public ResponseEntity<Result<?>> exchange(@Valid @RequestBody OAuthExchangeDTO body,
            HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        log.info("兑换第三方授权票据");
        WebSession side = WebSession.forPath(request.getServletPath());
        Object result = authService.exchange(side, body, request, browser);
        if (result instanceof LoginSession login) {
            OAuthLoginResultVO loginResult = new OAuthLoginResultVO("LOGIN", login.response());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, side.issue(login))
                    .body(Result.ok(loginResult));
        }
        return ResponseEntity.ok().body(Result.ok(result));
    }
}
