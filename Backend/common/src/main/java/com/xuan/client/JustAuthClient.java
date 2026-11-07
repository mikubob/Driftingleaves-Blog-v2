package com.xuan.client;

import cn.hutool.core.util.StrUtil;
import com.xuan.exception.BusinessException;
import com.xuan.policy.UrlPolicy;
import com.xuan.properties.AuthProperties;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.store.RedisOAuthStore;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.config.AuthDefaultSource;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthQqRequest;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthChecker;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 第三方登录客户端，只支持名单中的 GET 回调平台，默认关闭，配置不完整时不可用。
 */
@Component
@RequiredArgsConstructor
public class JustAuthClient implements OAuthProviderClient {

    private static final System.Logger LOGGER = System.getLogger(JustAuthClient.class.getName());
    private static final Map<String, String> SOURCES = Map.of("GITHUB", "GitHub", "GITEE", "Gitee", "QQ", "QQ");
    private final AuthProperties properties;
    private final PublicSiteProperties sites;
    private final RedisOAuthStore states;

    /**
     * 查询已启用且配置完整的第三方平台。
     */
    @Override
    public List<Provider> providers(String site) {
        if (!sites.origin(site).startsWith("https://")) {
            return List.of();
        }
        return enabledSources().stream().sorted().map(source -> new Provider(source, SOURCES.get(source))).toList();
    }

    /**
     * 获取当前可用的第三方平台代码。
     */
    @Override
    public Set<String> enabledSources() {
        return SOURCES.keySet().stream().filter(this::configured).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 检查第三方平台是否可用，不可用时返回 404。
     */
    @Override
    public void requireEnabled(String source) {
        if (!enabledSources().contains(source)) {
            throw new BusinessException(404, "NOT_FOUND", "平台不存在或未启用");
        }
    }

    @Override
    public String callbackPath(String source) {
        requireEnabled(source);
        return URI.create(properties.getProviders().get(source).getRedirectUri()).getRawPath();
    }

    /**
     * 生成指定平台的授权地址。
     */
    @Override
    public String authorize(String source, String state) {
        requireEnabled(source);
        return request(source).authorize(state);
    }

    /**
     * 校验授权码和 state，获取平台确认的用户身份。
     */
    @Override
    public Identity verify(String source, String state, String code) {
        // 1. 先从服务端名单里找平台客户端，不能相信客户端自报的身份。
        requireEnabled(source);
        AuthCallback callback = AuthCallback.builder().state(state).code(code).build();
        AuthRequest request = request(source);

        String stage = "校验回调";
        try {
            // 2. 自行执行 JustAuth 的同样检查，避免 login() 记录原始平台响应。
            AuthChecker.checkCode(AuthDefaultSource.valueOf(source), callback);
            AuthChecker.checkState(state, AuthDefaultSource.valueOf(source), states);

            // 3. 用授权码换取平台确认的身份，只向本站传必要资料。
            stage = "兑换授权码";
            AuthToken token = request.getAccessToken(callback);
            stage = "读取平台身份";
            AuthUser user = request.getUserInfo(token);
            return new Identity(user.getUuid(), user.getNickname(), user.getAvatar());
        } catch (AuthException exception) {
            String reason = "GITHUB".equals(source) ? githubError(exception.getErrorMsg()) : "UNKNOWN";
            LOGGER.log(System.Logger.Level.WARNING, "平台授权被拒绝，平台={0}，阶段={1}，SDK错误码={2}，平台错误={3}",
                    source, stage, Integer.toString(exception.getErrorCode()), reason);
            throw exception;
        }
    }

    /** 识别 GitHub 的固定错误，只记录已知类别，避免把平台响应内容写入日志。 */
    private static String githubError(String message) {
        return switch (message) {
            case "The client_id and/or client_secret passed are incorrect." -> "incorrect_client_credentials";
            case "The redirect_uri MUST match the registered callback URL for this application." -> "redirect_uri_mismatch";
            case "The code passed is incorrect or expired." -> "bad_verification_code";
            case "The user must have a verified primary email." -> "unverified_user_email";
            case null, default -> "UNKNOWN";
        };
    }

    /**
     * 创建指定平台的客户端，只使用服务端名单和配置，保留 state 校验。
     */
    private AuthRequest request(String source) {
        // 1. 先按服务端配置准备授权参数，JustAuth 的 state 检查不能关。
        AuthProperties.Provider provider = properties.getProviders().get(source);
        AuthConfig config = AuthConfig.builder().clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret()).redirectUri(provider.getRedirectUri())
                .ignoreCheckState(false).build();

        // 2. 只创建名单里的平台客户端，state 缓存共用 Redis。
        return switch (source) {
            case "GITHUB" -> new AuthGithubRequest(config, states);
            case "GITEE" -> new AuthGiteeRequest(config, states);
            case "QQ" -> new AuthQqRequest(config, states);
            default -> throw new IllegalArgumentException("平台不在服务端白名单");
        };
    }

    /**
     * 检查第三方平台配置，开关、凭据和固定回调地址都有效时才可用。
     */
    private boolean configured(String source) {
        // 1. 先看开关、凭据和地址是否齐全，缺配置的平台不能算可用登录方式。
        AuthProperties.Provider provider = properties.getProviders().get(source);
        if (provider == null || !provider.isEnabled() || blank(provider.getClientId()) || blank(
                provider.getClientSecret())
                || blank(provider.getRedirectUri()) || !UrlPolicy.valid(provider.getRedirectUri())) {
            return false;
        }

        // 每个平台只登记一个回调地址，两端共用该平台应用和用户身份。
        URI callback = URI.create(provider.getRedirectUri());
        String callbackPath = callback.getRawPath();
        String side;
        if (callbackPath.equals("/api/v1/blog/auth/oauth/" + source + "/callback")) {
            side = "blog";
        } else if (callbackPath.equals("/api/v1/admin/auth/oauth/" + source + "/callback")) {
            side = "admin";
        } else {
            return false;
        }
        String origin = sites.origin(side);
        return "https".equals(callback.getScheme())
                && origin.equals(PublicSiteProperties.originOf(callback))
                && callback.getRawQuery() == null && callback.getRawFragment() == null;
    }

    /**
     * 判断配置是否为空，null 和空白都算缺失。
     */
    private boolean blank(String value) {
        return StrUtil.nullToEmpty(value).isBlank();
    }
}
