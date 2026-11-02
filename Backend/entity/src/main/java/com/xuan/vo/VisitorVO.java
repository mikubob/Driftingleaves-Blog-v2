package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 后台访客资料，浏览次数按当前保留的关联明细统计，不另存累计值。
 */
public record VisitorVO(
        Long visitorId, String fingerprint, String ipAddress, String userAgent,
        String country, String province, String city, Long totalViews,
        Integer isBlocked, LocalDateTime blockedUntil, LocalDateTime firstVisitTime,
        LocalDateTime lastVisitTime, LocalDateTime createTime, LocalDateTime updateTime) {
}
