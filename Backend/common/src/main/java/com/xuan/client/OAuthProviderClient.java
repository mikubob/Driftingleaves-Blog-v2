package com.xuan.client;

import java.util.List;
import java.util.Set;

/** 外部授权平台客户端边界，业务只接收平台验证后的身份，不接收请求方声明的 uuid。 */
public interface OAuthProviderClient {
    List<Provider> providers();
    Set<String> enabledSources();
    void requireEnabled(String source);
    String authorize(String source, String state);
    Identity verify(String source, String state, String code);

    /** 配置完整的公开平台描述，不携带应用凭据或依赖业务响应模型。 */
    record Provider(String source, String displayName) { }

    /** 不携带第三方 Token，uuid 原样保留大小写和空格。 */
    record Identity(String uuid, String nickname, String avatar) { }
}
