package com.xuan;

import com.xuan.properties.AuthProperties;
import com.xuan.store.CaptchaStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 旧业务用例补齐真实 Redis 验证码，验证码拒绝用例直接构造原始请求。 */
final class CaptchaTestSupport {

    private CaptchaTestSupport() {
    }

    static MockHttpServletRequestBuilder withCaptcha(MockHttpServletRequestBuilder builder,
            StringRedisTemplate redis, AuthProperties auth) {
        return builder.with(request -> {
            String path = request.getServletPath();
            if ("POST".equals(request.getMethod()) && path.matches(
                    "/(admin|blog)/auth/(register|login|email-code|email-login|oauth/[^/]+/authorize)"
                    + "|/blog/articles/[^/]+/comments|/blog/messages|/admin/messages/[^/]+/replies")
                    || "PUT".equals(request.getMethod()) && path.matches("/blog/me/messages/[^/]+")) {
                String captchaId = new CaptchaStore(redis, auth).issue("012345");
                request.addHeader("X-Captcha-Id", captchaId);
                request.addHeader("X-Captcha-Code", "012345");
            }
            return request;
        });
    }
}
