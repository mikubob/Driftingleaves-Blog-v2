package com.xuan;

import com.xkcoding.http.HttpUtil;
import com.xkcoding.http.support.HttpHeader;
import com.xkcoding.http.support.SimpleHttpResponse;
import com.xuan.properties.AuthProperties;
import com.xuan.service.JustAuthGateway;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthGithubRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

/** 外部 HTTP 响应替身验证固定 JustAuth 与 Fastjson 兼容包的真实解析。 */
class JustAuthParsingTest {
    @Test
    void githubResponseParsingUsesCompatibleFastjsonApi() {
        AuthConfig config = AuthConfig.builder().clientId("test").clientSecret("test")
                .redirectUri("https://blog.example.test/api/v1/auth/oauth/GITHUB/callback").build();
        AuthGithubRequest request = new AuthGithubRequest(config) {
            @Override
            protected String doPostAuthorizationCode(String code) {
                return "access_token=boundary-token&scope=user&token_type=bearer";
            }
        };
        var token = request.getAccessToken(AuthCallback.builder().code("boundary-code").build());
        assertThat(token.getAccessToken()).isEqualTo("boundary-token");
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), isNull(), any(HttpHeader.class), eq(false)))
                    .thenReturn(new SimpleHttpResponse(true, 200, Map.of(),
                            "{\"id\":9223372036854775807,\"login\":\"leaf\",\"name\":\"叶\",\"avatar_url\":\"https://example.test/a.png\"}", null));
            AuthUser user = request.getUserInfo(token);
            assertThat(user.getUuid()).isEqualTo("9223372036854775807");
            assertThat(user.getNickname()).isEqualTo("叶");
            assertThat(user.getAvatar()).isEqualTo("https://example.test/a.png");
            http.when(() -> HttpUtil.get(anyString(), isNull(), any(HttpHeader.class), eq(false)))
                    .thenReturn(new SimpleHttpResponse(true, 200, Map.of(),
                            "{\"error\":\"bad_verification_code\",\"error_description\":\"boundary-error\"}", null));
            assertThatThrownBy(() -> request.getUserInfo(token)).isInstanceOf(AuthException.class);
        }
    }

    @Test
    void providersAreDisabledUntilEveryRequiredSettingIsPresent() {
        AuthProperties properties = new AuthProperties();
        JustAuthGateway gateway = new JustAuthGateway(properties, null);
        assertThat(gateway.providers()).isEmpty();
        AuthProperties.Provider github = new AuthProperties.Provider();
        github.setEnabled(true);
        properties.getProviders().put("GITHUB", github);
        assertThat(gateway.providers()).isEmpty();
    }
}
