package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统计标识不授予身份；完整 IP 仅管理端可见，时间由服务端确定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("blog_visitor")
public class BlogVisitor implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 访客ID
    @TableId(type = IdType.AUTO)
    private Long visitorId;

    // 访客指纹标识
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

    // 是否拉黑 0-否 1-是
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
