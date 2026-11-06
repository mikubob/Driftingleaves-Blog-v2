package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.client.OAuthProviderClient;
import com.xuan.dto.EmailCodeDTO;
import com.xuan.dto.EmailLoginDTO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.OAuthAuthorizeDTO;
import com.xuan.dto.OAuthExchangeDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.exception.BusinessException;
import com.xuan.model.LoginSession;
import com.xuan.ratelimit.AuthRateLimiter;
import com.xuan.result.Result;
import com.xuan.security.WebSession;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.OAuthLoginResultVO;
import com.xuan.vo.ProviderVO;
import com.xuan.vo.SessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * 登录和第三方授权接口，登录 Cookie 与 OAuth 浏览器标识分开。
 */
@RestController("blogAuthController")
@RequestMapping("/blog/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 第三方授权的浏览器标识 Cookie，不作为登录凭据。 */
    public static final String BROWSER_COOKIE = "__Host-dl-oauth-browser";
    private final IUserService users;
    private final SessionManager sessions;
    private final IOAuthService oauth;
    private final OAuthProviderClient providers;
    private final AuthRateLimiter limiter;
    private final CaptchaStore captchas;

    /**
     * 注册用户，先校验参数和请求频率，角色、状态及密码加密由业务层处理；成功返回 201，不自动登录。
     *
     * @param body 通过 Jakarta Validation 校验的允许的注册字段
     * @param request 用于识别客户端 IP 的当前 HTTP 请求
     * @param browser 已有 OAuth 浏览器标识，可缺失
     * @return 201 响应及不含密码哈希的本人资料
     */
    @PostMapping("/register")
    public ResponseEntity<Result<MeVO>> register(@Valid @RequestBody RegisterDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 1. 注册写库前先检查 IP 和浏览器对应的请求次数。
        limiter.register(request, browser);
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));

        // 2. 交给业务事务创建账号，只返回资料和 201，不在注册时自动登录。
        return ResponseEntity.status(201).body(Result.ok(users.register(body)));
    }

    /**
     * 使用用户名和密码登录，先检查失败次数，只在凭据错误时计数，密码校验、加锁和 Token 创建由业务层处理。
     *
     * @param body 已校验的用户名和密码长度的登录请求
     * @param request 用于按 IP 和统一格式后的用户名限流的请求
     * @return 登录 Cookie 和脱敏资料
     */
    @PostMapping("/login")
    @RateLimit(value = "login-total", limit = 30)
    public ResponseEntity<Result<SessionVO>> login(@Valid @RequestBody LoginDTO body, HttpServletRequest request) {
        // 1. 先看失败次数是否超限，超限后就不再验证密码。
        limiter.checkLogin(request, body.username());
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));

        // 2. 用户业务一起完成凭据验证、会话签发和登录时间写入。
        try {
            return WebSession.BLOG.login(users.login(body, SessionManager.BLOG_TIMEOUT_SECONDS));
        } catch (BusinessException exception) {
            // 3. 只累计凭据错误，基础服务出错或账号禁用不能算密码输错。
            if ("AUTH_FAILED".equals(exception.getCode())) {
                limiter.loginFailed(request, body.username());
            }
            throw exception;
        }
    }

    /** 发送注册或前台登录验证码，先校验图形验证码，不返回邮件中的数字。 */
    @PostMapping("/email-code")
    public Result<Void> emailCode(@Valid @RequestBody EmailCodeDTO body, HttpServletRequest request) {
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
        limiter.email(request, body.email());
        users.sendEmailCode(body);
        return Result.ok(null);
    }

    /** 使用邮箱验证码登录，图形验证码也必须通过，会话有效期与密码登录一致。 */
    @PostMapping("/email-login")
    @RateLimit(value = "login-total", limit = 30)
    public ResponseEntity<Result<SessionVO>> emailLogin(@Valid @RequestBody EmailLoginDTO body, HttpServletRequest request) {
        limiter.checkLogin(request, body.email());
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
        try {
            return WebSession.BLOG.login(users.loginEmail(body, SessionManager.BLOG_TIMEOUT_SECONDS));
        } catch (BusinessException exception) {
            if ("AUTH_FAILED".equals(exception.getCode()) || "EMAIL_CODE_INVALID".equals(exception.getCode())) {
                limiter.loginFailed(request, body.email());
            }
            throw exception;
        }
    }

    /**
     * 撤销当前端会话，再清除登录 Cookie；没有会话也返回成功。
     *
     * @return data 为 null 的成功结果
     */
    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout() {
        return WebSession.BLOG.logout(sessions);
    }

    /**
     * 查询可用的第三方平台，不返回应用id、密钥或回调配置。
     *
     * @return 仅含平台标识和展示名称的列表，无可用平台时为空列表
     */
    @GetMapping("/oauth/providers")
    public Result<List<ProviderVO>> providers() {
        return Result.ok(providers.providers().stream()
                .filter(provider -> providers.callbackPath(provider.source()).startsWith("/api/v1/blog/"))
                .map(provider -> new ProviderVO(provider.source(), provider.displayName())).toList());
    }

    /**
     * 发起第三方授权并设置流程 Cookie，绑定时还要检查原会话和最近认证。
     *
     * @param source 服务端已启用的平台标识
     * @param body 仅包含 LOGIN 或 BIND 操作类型的请求
     * @param request 用于 IP 限流的当前请求
     * @param browser 已有浏览器标识；缺失或格式无效时重新生成
     * @return 授权 URL、state 有效秒数及安全 Cookie 响应头
     */
    @PostMapping("/oauth/{source}/authorize")
    public ResponseEntity<Result<OAuthAuthorizeVO>> authorize(@PathVariable String source,
            @Valid @RequestBody OAuthAuthorizeDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        if (body.operation() == OAuthAuthorizeDTO.Operation.BIND) {
            WebSession.requireExpectedUser(request.getHeader(WebSession.EXPECTED_USER), sessions.currentUserId());
        }
        // 1. 复用或创建只用于 OAuth 流程的浏览器标识。
        String id = browser != null && browser.matches("[A-Za-z0-9_-]{43}") ? browser : TokenUtils.random();

        // 2. 限流通过后发起授权，绑定时的原会话和最近认证由业务服务检查。
        limiter.oauth(request, id);
        if (body.operation() == OAuthAuthorizeDTO.Operation.LOGIN) {
            captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
        }
        OAuthAuthorizeVO result = oauth.authorize(source, body.operation(), id);

        // 3. 设置只关联流程的安全 Cookie，再返回授权 URL。
        ResponseCookie cookie = ResponseCookie.from(BROWSER_COOKIE, id).secure(true).httpOnly(true)
                .sameSite("Lax").path("/").maxAge(Duration.ofSeconds(600)).build();
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(Result.ok(result));
    }

    /**
     * 处理平台顶层 GET 回调，核对浏览器、state 和绑定原会话后跳固定完成页；平台不用带登录 Cookie，流程不可信就报 JSON 错误，不跳转。
     *
     * @param source 回调路径中的服务端允许的平台标识
     * @param state 平台带回的一次性 state，由业务服务验证
     * @param code 成功授权时的平台授权码，失败回调可缺失
     * @param error 平台取消或失败标记，不向前端透传平台描述
     * @param browser 当前浏览器关联 Cookie，必须匹配发起流程
     * @return 可信流程的 303 Location 响应，不直接返回本站 Token
     */
    @GetMapping("/oauth/{source}/callback")
    @RateLimit(value = "oauth-callback", limit = 30)
    public ResponseEntity<Void> callback(@PathVariable String source, @RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String error,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        return ResponseEntity.status(303).location(URI.create(oauth.callback(source, state, code, error, browser)))
                .build();
    }

    /**
     * 在同一浏览器兑换一次性结果票据，绑定必须带发起时的原会话 Cookie。
     *
     * @param body 通过格式校验的 60 秒一次性结果票据
     * @param request 用于 IP 限流的当前请求
     * @param browser 必须与票据关联一致的浏览器标识
     * @return LOGIN 的会话分支或 BIND 的绑定分支，二者不混合
     */
    @PostMapping("/oauth/exchange")
    public ResponseEntity<Result<?>> exchange(@Valid @RequestBody OAuthExchangeDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 限流后核对浏览器和一次性票据。
        limiter.oauth(request, browser);

        // 2. 登录会返回新会话，绑定只确认原账号的绑定结果，不发新会话，也不切换账号。
        Object result = oauth.exchange(body.ticket(), browser,
                SessionManager.BLOG_TIMEOUT_SECONDS, request.getHeader(WebSession.EXPECTED_USER));
        if (result instanceof LoginSession login) {
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, WebSession.BLOG.issue(login))
                    .body(Result.ok(new OAuthLoginResultVO("LOGIN", login.response())));
        }
        return ResponseEntity.ok().body(Result.ok(result));
    }
}
