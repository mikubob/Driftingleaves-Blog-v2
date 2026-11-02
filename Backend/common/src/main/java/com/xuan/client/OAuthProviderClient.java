package com.xuan.client;

import java.util.List;
import java.util.Set;

/**
 * 第三方授权接口，只接受平台验证过的身份，不接受客户端自报的 uuid。
 */
public interface OAuthProviderClient {

    /**
     * 查询可用的第三方平台，只返回已启用且配置完整的平台名称。
     */
    List<Provider> providers();

    /**
     * 获取可用的平台代码，不包括配置缺失的平台。
     */
    Set<String> enabledSources();

    /**
     * 检查平台是否可用，不存在、未启用或配置不完整时返回 404。
     */
    void requireEnabled(String source);

    /**
     * 生成第三方授权地址，使用服务端配置和本次 state。
     */
    String authorize(String source, String state);

    /**
     * 校验第三方授权码和 state，返回平台确认的身份及公开资料。
     */
    Identity verify(String source, String state, String code);

    /**
     * 第三方平台公开资料，不包含应用凭据，不依赖业务响应类。
     */
    record Provider(String source, String displayName) {

    }

    /**
     * 第三方用户身份，不传递平台 Token，uuid 的大小写和空格保持原样。
     */
    record Identity(String uuid, String nickname, String avatar) {

    }
}
