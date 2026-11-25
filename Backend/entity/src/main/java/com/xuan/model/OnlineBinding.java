package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 服务端确认的统计身份，只保存原 Token 摘要与到期时间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineBinding implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 站点编码
    private String siteCode;

    // 请求来源Origin
    private String origin;

    // 访客指纹标识
    private String fingerprint;

    // 登录用户ID
    private Long userId;

    // 原会话令牌摘要
    private String tokenHash;

    // 票据到期时间
    private long expiresAt;

    /**
     * 分隔符不能出现在已校验的站点、Origin 和统计标识里。
     */
    public String browserKey() {
        return siteCode + "\n" + origin + "\n" + fingerprint;
    }
}
