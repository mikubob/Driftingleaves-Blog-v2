package com.xuan.controller.auth;

import com.xuan.utils.TokenUtils;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.OAuthAuthorizeDTO;
import com.xuan.dto.OAuthExchangeDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.result.Result;
import com.xuan.ratelimit.AuthRateLimiter;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.client.OAuthProviderClient;
import com.xuan.session.SessionManager;
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

/** 身份入口；OAuth Cookie 仅绑定浏览器，条件 BIND 鉴权在协调服务执行。 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    /** 仅用于 OAuth 浏览器关联的安全 Cookie 名，不承载本站登录凭据。 */
    public static final String BROWSER_COOKIE = "__Host-dl-oauth-browser";
    private final IUserService users;
    private final SessionManager sessions;
    private final IOAuthService oauth;
    private final OAuthProviderClient providers;
    private final AuthRateLimiter limiter;
    private final AuthProperties properties;

    /**
     * 接收公开注册请求，入口校验和限流通过后返回新用户资料。
     * <p>角色、启用状态及密码编码由用户服务确定；成功返回 201，不自动签发登录会话。</p>
     *
     * @param body 通过 Jakarta Validation 校验的注册白名单字段
     * @param request 用于识别客户端 IP 的当前 HTTP 请求
     * @param browser 已有 OAuth 浏览器标识，可缺失
     * @return 201 响应及不含密码哈希的本人资料
     */
    @PostMapping("/register")
    public ResponseEntity<Result<MeVO>> register(@Valid @RequestBody RegisterDTO body, HttpServletRequest request,
                                                @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 1. 在注册写库前检查 IP 和浏览器维度的频率。
        limiter.register(request, browser);
        // 2. 由服务事务创建账号，仅返回资料和 201，不在注册入口自动登录。
        return ResponseEntity.status(201).body(Result.ok(users.register(body)));
    }

    /**
     * 接收用户名密码登录请求，执行失败次数限制并返回本站会话。
     * <p>仅凭据错误累计登录失败；身份验证、行锁和会话签发由用户服务负责。</p>
     *
     * @param body 已校验用户名和密码长度边界的登录请求
     * @param request 用于 IP 与归一用户名维度限流的当前请求
     * @return 仅成功登录返回的独立 Bearer 会话
     */
    @PostMapping("/login")
    public Result<SessionVO> login(@Valid @RequestBody LoginDTO body, HttpServletRequest request) {
        // 1. 先检查失败次数是否超限，避免继续执行密码验证。
        limiter.checkLogin(request, body.username());
        // 2. 将凭据验证、会话签发和登录时间提交交由用户服务完成。
        try {
            return Result.ok(users.login(body));
        } catch (BusinessException exception) {
            // 3. 仅累计凭据错误，基础服务故障和账号禁用不计作密码失败。
            if ("AUTH_FAILED".equals(exception.getCode())) {
                limiter.loginFailed(request, body.username());
            }
            throw exception;
        }
    }

    /**
     * 幂等退出当前 Bearer 会话。
     * <p>无 Token 或 Token 已失效仍成功，不扩展为退出该用户的全部设备。</p>
     *
     * @return data 为 null 的成功结果
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        sessions.logout();
        return Result.ok(null);
    }

    /**
     * 公开查询当前可用的第三方平台。
     * <p>仅列服务端配置完整且启用的平台，不返回应用 ID、密钥或回调配置。</p>
     *
     * @return 仅含平台标识和展示名称的列表，无可用平台时为空列表
     */
    @GetMapping("/oauth/providers")
    public Result<List<ProviderVO>> providers() {
        return Result.ok(providers.providers().stream()
                .map(provider -> new ProviderVO(provider.source(), provider.displayName())).toList());
    }

    /**
     * 发起第三方登录或绑定，并设置浏览器关联 Cookie。
     * <p>校验明确的前端 Origin；LOGIN 公开，BIND 由协调服务检查原会话和最近认证，不接收客户端目标用户或回跳地址。</p>
     *
     * @param source 服务端已启用的白名单平台标识
     * @param body 仅包含 LOGIN 或 BIND 操作类型的请求
     * @param request 用于 Origin 校验和 IP 限流的当前请求
     * @param browser 已有浏览器标识；缺失或格式无效时重新生成
     * @return 授权 URL、state 有效秒数及安全 Cookie 响应头
     */
    @PostMapping("/oauth/{source}/authorize")
    public ResponseEntity<Result<OAuthAuthorizeVO>> authorize(@PathVariable String source,
            @Valid @RequestBody OAuthAuthorizeDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 1. 校验前端 Origin，再复用或生成浏览器标识；该 Cookie 不承载登录身份。
        requireOrigin(request);
        String id = browser != null && browser.matches("[A-Za-z0-9_-]{43}") ? browser : TokenUtils.random();
        // 2. 限流后发起授权，BIND 的原会话和最近认证由协调服务检查。
        limiter.oauth(request, id);
        OAuthAuthorizeVO result = oauth.authorize(source, body.operation(), id);
        // 3. 设置仅用于流程关联的安全 Cookie，并返回授权 URL。
        ResponseCookie cookie = ResponseCookie.from(BROWSER_COOKIE, id).secure(true).httpOnly(true)
                .sameSite("Lax").path("/").maxAge(Duration.ofSeconds(600)).build();
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(Result.ok(result));
    }

    /**
     * 处理平台顶层 GET 回调，将可信流程重定向到固定前端完成页。
     * <p>平台无需附加本站 Bearer；浏览器、state 和 BIND 原会话由协调服务核对。不可信流程直接返回统一 JSON 错误，不跳转。</p>
     *
     * @param source 回调路径中的服务端白名单平台标识
     * @param state 平台带回的一次性 state，由协调服务验证
     * @param code 成功授权时的平台授权码，失败回调可缺失
     * @param error 平台取消或失败标记，不向前端透传平台描述
     * @param browser 当前浏览器关联 Cookie，必须匹配发起流程
     * @return 可信流程的 303 Location 响应，不直接返回本站 Token
     */
    @GetMapping("/oauth/{source}/callback")
    public ResponseEntity<Void> callback(@PathVariable String source, @RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String error,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        return ResponseEntity.status(303).location(URI.create(oauth.callback(source, state, code, error, browser))).build();
    }

    /**
     * 在同浏览器中兑换一次性 OAuth 结果票据。
     * <p>校验 Origin 与请求频率；BIND 必须携带发起时的原 Token，不能用 Cookie 或其他会话替代。</p>
     *
     * @param body 通过格式校验的 60 秒一次性结果票据
     * @param request 用于 Origin、IP 校验及携带原 Bearer 的当前请求
     * @param browser 必须与票据关联一致的浏览器标识
     * @return LOGIN 的会话分支或 BIND 的绑定分支，二者不混合
     */
    @PostMapping("/oauth/exchange")
    public Result<Object> exchange(@Valid @RequestBody OAuthExchangeDTO body, HttpServletRequest request,
            @CookieValue(value = BROWSER_COOKIE, required = false) String browser) {
        // 1. 先校验 Origin 和请求频率，再由服务核对浏览器及一次性票据。
        requireOrigin(request);
        limiter.oauth(request, browser);
        // 2. LOGIN 返回新会话；BIND 只确认原账号的绑定结果，不签发或切换会话。
        return Result.ok(oauth.exchange(body.ticket(), browser));
    }

    /**
     * 校验 OAuth 发起或兑换请求来自明确配置的前端 origin。
     * <p>缺失和未授权 Origin 均拒绝，不能用浏览器 Cookie 代替来源校验。</p>
     *
     * @param request 浏览器发起的当前 HTTP 请求
     */
    private void requireOrigin(HttpServletRequest request) {
        if (!properties.getAllowedOrigins().contains(request.getHeader("Origin"))) {
            throw new BusinessException(403, "FORBIDDEN", "请求Origin不被允许");
        }
    }
}
