package com.xuan.client;

import java.util.List;
import java.util.Set;

/**
 * 这里只接收平台验证过的身份，不能把客户端自报的 uuid 当作登录依据。
 */
public interface OAuthProviderClient {

    /**
     * 列出已启用且配置完整的平台，只返回前台需要的名称。
     */
    List<Provider> providers();

    /**
     * 返回当前可用的平台码，不把缺少配置的平台算作登录方式。
     */
    Set<String> enabledSources();

    /**
     * 平台不存在、未启用或配置不完整时统一返回 404。
     */
    void requireEnabled(String source);

    /**
     * 使用服务端平台配置和本次 state 生成授权地址。
     */
    String authorize(String source, String state);

    /**
     * 向平台核验授权码和 state，只返回已确认的身份及公开资料。
     */
    Identity verify(String source, String state, String code);

    /**
     * 给前台看的平台资料只含公开信息，不带应用凭据，也不依赖业务响应类。
     */
    record Provider(String source, String displayName) {

    }

    /**
     * 平台 Token 不往后传，uuid 的大小写和空格原样保留。
     */
    record Identity(String uuid, String nickname, String avatar) {

    }
}
