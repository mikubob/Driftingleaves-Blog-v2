package com.xuan.client;

import java.util.List;
import java.util.Set;

/** 这里只接收平台验证过的身份，不能把客户端自报的 uuid 当作登录依据。 */
public interface OAuthProviderClient {
    List<Provider> providers();
    Set<String> enabledSources();
    void requireEnabled(String source);
    String authorize(String source, String state);
    Identity verify(String source, String state, String code);

    /** 给前台看的平台资料只含公开信息，不带应用凭据，也不依赖业务响应类。 */
    record Provider(String source, String displayName) { }

    /** 平台 Token 不往后传，uuid 的大小写和空格原样保留。 */
    record Identity(String uuid, String nickname, String avatar) { }
}
