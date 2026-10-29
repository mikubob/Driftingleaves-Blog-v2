package com.xuan.service;

import com.xuan.vo.ProviderVO;
import java.util.List;
import java.util.Set;

/** 唯一外部平台边界，业务只接收平台验证后的身份，不接收客户端 uuid。 */
public interface OAuthProviderGateway {
    List<ProviderVO> providers();
    Set<String> enabledSources();
    void requireEnabled(String source);
    String authorize(String source, String state);
    Identity verify(String source, String state, String code);

    /** 不携带第三方 Token，uuid 原样保留大小写和空格。 */
    record Identity(String uuid, String nickname, String avatar) { }
}
