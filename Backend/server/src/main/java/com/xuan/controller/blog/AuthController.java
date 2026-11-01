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
import com.xuan.properties.AuthProperties;
import com.xuan.ratelimit.AuthRateLimiter;
import com.xuan.result.Result;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.ProviderVO;
import com.xuan.vo.SessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 登录和 OAuth 入口在这里；Cookie 只关联浏览器，绑定时需要的原会话由业务服务检查。
 */
@RestController("blogAuthController")
@RequestMapping("/blog/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 这个安全 Cookie 只关联 OAuth 浏览器，不是本站登录凭据。 */
    public static final String BROWSER_COOKIE = "__Host-dl-oauth-browser";
    private final IUserService users;
    private final SessionManager sessions;
    private final IOAuthService oauth;
    private final OAuthProviderClient providers;
    private final AuthRateLimiter limiter;
    private final AuthProperties properties;
    private final CaptchaStore captchas;

    /**
     * 注册先检查输入和频率，再创建用户返回 201；角色、状态和密码编码由用户业务处理，不自动登录。
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
     * 用户名密码登录先检查失败次数；只有凭据错误才计数，验证密码、锁用户和发 Token 交给用户业务。
     *
     * @param body 已校验的用户名和密码长度的登录请求
     * @param request 用于 IP 与归一用户名维度限流的当前请求
     * @return 仅成功登录返回的独立 Bearer 会话
     */
    @PostMapping("/login")
    @RateLimit(value = "login-total", limit = 30)
    public Result<SessionVO> login(@Valid @RequestBody LoginDTO body, HttpServletRequest request) {
        // 1. 先看失败次数是否超限，超限后就不再验证密码。
        limiter.checkLogin(request, body.username());
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));

        // 2. 用户业务一起完成凭据验证、会话签发和登录时间写入。
        try {
            return Result.ok(users.login(body, SessionManager.BLOG_TIMEOUT_SECONDS));
        } catch (BusinessException exception) {
            // 3. 只累计凭据错误，基础服务出错或账号禁用不能算密码输错。
            if ("AUTH_FAILED".equals(exception.getCode())) {
                limiter.loginFailed(request, body.username());
            }
            throw exception;
        }
    }

    /** 图形验证码通过后发送注册或前台登录邮件，接口不返回邮件中的数字。 */
    @PostMapping("/email-code")
    public Result<Void> emailCode(@Valid @RequestBody EmailCodeDTO body, HttpServletRequest request) {
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
        limiter.email(request, body.email());
        users.sendEmailCode(body);
        return Result.ok(null);
    }

    /** 邮箱验证码和图形验证码同时通过后，签发与密码登录同期限的前台会话。 */
    @PostMapping("/email-login")
    @RateLimit(value = "login-total", limit = 30)
    public Result<SessionVO> emailLogin(@Valid @RequestBody EmailLoginDTO body, HttpServletRequest request) {
        limiter.checkLogin(request, body.email());
        captchas.verify(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
        try {
            return Result.ok(users.loginEmail(body));
        } catch (BusinessException exception) {
            if ("AUTH_FAILED".equals(exception.getCode()) || "EMAIL_CODE_INVALID".equals(exception.getCode())) {
                limiter.loginFailed(request, body.email());
            }
            throw exception;
        }
    }

    /**
     * 退出当前 Bearer 会话，没传 Token 或早已失效也返回成功；不影响这个用户的其他设备。
     *
     * @return data 为 null 的成功结果
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        sessions.logout();
        return Result.ok(null);
    }

    /**
     * 公开列出已启用且配置齐全的平台，不返回应用 ID、密钥或回调配置。
     *
     * @return 仅含平台标识和展示名称的列表，无可用平台时为空列表
     */
    @GetMapping("/oauth/providers")
    public Result<List<ProviderVO>> providers() {
        return Result.ok(providers.providers().stream()
                .map(provider -> new ProviderVO(provider.source(), provider.displayName())).toList());
    }

    /**
     * 发起第三方登录或绑定并设置浏览器 Cookie；先查前端 Origin，绑定还要查原会话和最近认证，不能自选目标用户或回跳地址。
     *
     * @param source 服务端已启用的平台标识
     * @param body 仅包含 LOGIN 或 BIND 操作类型的请求
     * @param request 用于 Origin 校验和 IP 限流的当前请求
     * @param browser 已有浏览器标识；缺失或格式无效时重新生成
     * @return 授权 URL、state 有效秒数及安全 Cookie 响应头
     */
    @PostMapping("/oauth/{source}/authorize")
    public ResponseEntity<Result<OAuthAuthorizeVO>> authorize(@PathVariable String source,
            @Valid @RequestBody OAuthAuthorizeDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 1. 先查前端 Origin，再复用或创建浏览器标识；这个 Cookie 不能代表登录身份。
        requireOrigin(request);
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
     * 处理平台顶层 GET 回调，核对浏览器、state 和绑定原会话后跳固定完成页；平台不用带本站 Bearer，流程不可信就报 JSON 错误，不跳转。
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
     * 在同一浏览器兑换一次性结果票据，先查 Origin 和频率；绑定必须带发起时的原 Token，不能换成 Cookie 或另一枚 Token。
     *
     * @param body 通过格式校验的 60 秒一次性结果票据
     * @param request 用于 Origin、IP 校验及携带原 Bearer 的当前请求
     * @param browser 必须与票据关联一致的浏览器标识
     * @return LOGIN 的会话分支或 BIND 的绑定分支，二者不混合
     */
    @PostMapping("/oauth/exchange")
    public Result<Object> exchange(@Valid @RequestBody OAuthExchangeDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 1. 先查 Origin 和频率，再交给业务核对浏览器及一次性票据。
        requireOrigin(request);
        limiter.oauth(request, browser);

        // 2. 登录会返回新会话，绑定只确认原账号的绑定结果，不发新会话，也不切换账号。
        return Result.ok(oauth.exchange(body.ticket(), browser, SessionManager.BLOG_TIMEOUT_SECONDS));
    }

    /**
     * OAuth 发起和兑换只能来自配置过的前端 origin；缺失或没授权都拒绝，不能拿 Cookie 代替来源检查。
     *
     * @param request 浏览器发起的当前 HTTP 请求
     */
    private void requireOrigin(HttpServletRequest request) {
        if (!properties.getAllowedOrigins().contains(request.getHeader("Origin"))) {
            throw new BusinessException(403, "FORBIDDEN", "请求Origin不被允许");
        }
    }
}
