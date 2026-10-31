package com.xuan.model;

/** 服务端确认的统计身份，只保存原 Token 摘要与到期时间。 */
public record OnlineBinding(String siteCode, String origin, String fingerprint,
        Long userId, String tokenHash, long expiresAt) {

    /** 分隔符不能出现在已校验的站点、Origin 和统计标识里。 */
    public String browserKey() {
        return siteCode + "\n" + origin + "\n" + fingerprint;
    }
}
