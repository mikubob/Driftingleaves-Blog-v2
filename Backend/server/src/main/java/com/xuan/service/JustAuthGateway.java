package com.xuan.service;

import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.vo.ProviderVO;
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

/** 固定普通 GET 回调平台白名单；默认全关闭，缺配置的平台不能用于登录方式保护。 */
@Component
@RequiredArgsConstructor
public class JustAuthGateway implements OAuthProviderGateway {
    private static final Map<String, String> SOURCES = Map.of("GITHUB", "GitHub", "GITEE", "Gitee", "QQ", "QQ");
    private final AuthProperties properties;
    private final RedisOAuthStore states;

    @Override
    public List<ProviderVO> providers() {
        return enabledSources().stream().sorted().map(source -> new ProviderVO(source, SOURCES.get(source))).toList();
    }

    @Override
    public Set<String> enabledSources() {
        return SOURCES.keySet().stream().filter(this::configured).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void requireEnabled(String source) {
        if (!enabledSources().contains(source)) {
            throw new BusinessException(404, "NOT_FOUND", "平台不存在或未启用");
        }
    }

    @Override
    public String authorize(String source, String state) {
        requireEnabled(source);
        return request(source).authorize(state);
    }

    @Override
    public Identity verify(String source, String state, String code) {
        // 1. 按服务端白名单取得平台适配器，不接收客户端声明的身份。
        requireEnabled(source);
        AuthCallback callback = AuthCallback.builder().state(state).code(code).build();
        AuthRequest request = request(source);
        // 2. 显式执行 JustAuth 相同的校验，避免其 login() 内部打印带平台响应的异常。
        AuthChecker.checkCode(AuthDefaultSource.valueOf(source), callback);
        AuthChecker.checkState(state, AuthDefaultSource.valueOf(source), states);
        // 3. 使用授权码换取平台身份，只将必要资料传入本站业务，不传递平台 Token。
        AuthUser user = request.getUserInfo(request.getAccessToken(callback));
        return new Identity(user.getUuid(), user.getNickname(), user.getAvatar());
    }

    private AuthRequest request(String source) {
        // 1. 从服务端配置构造授权参数，保持 JustAuth 的 state 校验开启。
        AuthProperties.Provider provider = properties.getProviders().get(source);
        AuthConfig config = AuthConfig.builder().clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret()).redirectUri(provider.getRedirectUri())
                .ignoreCheckState(false).build();
        // 2. 只实例化固定平台适配器，并共用 Redis state 缓存。
        return switch (source) {
            case "GITHUB" -> new AuthGithubRequest(config, states);
            case "GITEE" -> new AuthGiteeRequest(config, states);
            case "QQ" -> new AuthQqRequest(config, states);
            default -> throw new IllegalArgumentException("平台不在服务端白名单");
        };
    }

    private boolean configured(String source) {
        // 1. 先检查启用开关、凭据和地址完整性，不完整的平台不计为可用登录方式。
        AuthProperties.Provider provider = properties.getProviders().get(source);
        if (provider == null || !provider.isEnabled() || blank(provider.getClientId()) || blank(provider.getClientSecret())
                || !UrlPolicy.valid(provider.getRedirectUri()) || blank(provider.getRedirectUri())
                || !UrlPolicy.valid(properties.getFrontendCompleteUrl()) || blank(properties.getFrontendCompleteUrl())) {
            return false;
        }
        // 2. 再核对 HTTPS、固定回调路径和前端 origin，禁止动态回跳。
        URI callback = URI.create(provider.getRedirectUri());
        URI complete = URI.create(properties.getFrontendCompleteUrl());
        String origin = complete.getScheme() + "://" + complete.getRawAuthority();
        return "https".equals(callback.getScheme()) && "https".equals(complete.getScheme())
                && callback.getPath().equals("/api/v1/auth/oauth/" + source + "/callback")
                && callback.getRawQuery() == null && callback.getRawFragment() == null
                && complete.getPath().equals("/oauth/complete") && complete.getRawQuery() == null
                && complete.getRawFragment() == null && properties.getAllowedOrigins().contains(origin);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
