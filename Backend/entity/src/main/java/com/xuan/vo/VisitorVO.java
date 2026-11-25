package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台访客资料，浏览次数按当前保留的关联明细统计，不另存累计值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 访客ID
    private Long visitorId;

    // 访客指纹
    private String fingerprint;

    // IP地址
    private String ipAddress;

    // User-Agent
    private String userAgent;

    // 国家
    private String country;

    // 省份
    private String province;

    // 城市
    private String city;

    // 总浏览次数
    private Long totalViews;

    // 是否拉黑
    private Integer isBlocked;

    // 拉黑截止时间
    private LocalDateTime blockedUntil;

    // 拉黑IP地址
    private String blockedIpAddress;

    // 首次访问时间
    private LocalDateTime firstVisitTime;

    // 最后访问时间
    private LocalDateTime lastVisitTime;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
