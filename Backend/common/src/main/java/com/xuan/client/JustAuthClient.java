package com.xuan.client;

import cn.hutool.core.util.StrUtil;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.store.RedisOAuthStore;
import com.xuan.utils.UrlPolicy;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.config.AuthDefaultSource;
import me.zhyd.oauth.model.AuthCallback;
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
 * 只支持名单里的普通 GET 回调平台，默认都关闭；配置不全的平台不算可用登录方式。
 */
@Component
@RequiredArgsConstructor
public class JustAuthClient implements OAuthProviderClient {

    private static final Map<String, String> SOURCES = Map.of("GITHUB", "GitHub", "GITEE", "Gitee", "QQ", "QQ");
    private final AuthProperties properties;
    private final RedisOAuthStore states;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Provider> providers() {
        return enabledSources().stream().sorted().map(source -> new Provider(source, SOURCES.get(source))).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> enabledSources() {
        return SOURCES.keySet().stream().filter(this::configured).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEnabled(String source) {
        if (!enabledSources().contains(source)) {
            throw new BusinessException(404, "NOT_FOUND", "平台不存在或未启用");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String authorize(String source, String state) {
        requireEnabled(source);
        return request(source).authorize(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity verify(String source, String state, String code) {
        // 1. 先从服务端名单里找平台客户端，不能相信客户端自报的身份。
        requireEnabled(source);
        AuthCallback callback = AuthCallback.builder().state(state).code(code).build();
        AuthRequest request = request(source);

        // 2. 这里自己执行 JustAuth 的同样检查，避免 login() 把带平台响应的异常打进日志。
        AuthChecker.checkCode(AuthDefaultSource.valueOf(source), callback);
        AuthChecker.checkState(state, AuthDefaultSource.valueOf(source), states);

        // 3. 用授权码换取平台确认的身份，只向本站传必要资料，不传平台 Token。
        AuthUser user = request.getUserInfo(request.getAccessToken(callback));
        return new Identity(user.getUuid(), user.getNickname(), user.getAvatar());
    }

    /**
     * 只创建白名单中的平台客户端，使用服务端配置并保留 state 检查。
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
     * 检查开关、凭据及固定回调地址，缺少任一项就不开放该平台。
     */
    private boolean configured(String source) {
        // 1. 先看开关、凭据和地址是否齐全，缺配置的平台不能算可用登录方式。
        AuthProperties.Provider provider = properties.getProviders().get(source);
        if (provider == null || !provider.isEnabled() || blank(provider.getClientId()) || blank(
                provider.getClientSecret())
                || !UrlPolicy.valid(provider.getRedirectUri()) || blank(provider.getRedirectUri())
                || !UrlPolicy.valid(properties.getFrontendCompleteUrl()) || blank(
                properties.getFrontendCompleteUrl())) {
            return false;
        }

        // 2. 再检查 HTTPS、固定回调路径和前端 origin，不能临时换回跳地址。
        URI callback = URI.create(provider.getRedirectUri());
        URI complete = URI.create(properties.getFrontendCompleteUrl());
        String origin = complete.getScheme() + "://" + complete.getRawAuthority();
        return "https".equals(callback.getScheme()) && "https".equals(complete.getScheme())
                && (callback.getPath().equals("/api/v1/blog/auth/oauth/" + source + "/callback")
                || callback.getPath().equals("/api/v1/admin/auth/oauth/" + source + "/callback"))
                && callback.getRawQuery() == null && callback.getRawFragment() == null
                && complete.getPath().equals("/oauth/complete") && complete.getRawQuery() == null
                && complete.getRawFragment() == null && properties.getAllowedOrigins().contains(origin);
    }

    /**
     * null 或空白都视为配置缺失。
     */
    private boolean blank(String value) {
        return StrUtil.nullToEmpty(value).isBlank();
    }
}
