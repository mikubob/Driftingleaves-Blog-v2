package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统计标识不授予身份；IP只存脱敏值，时间由服务端确定。
 */
@Data
@TableName("blog_visitor")
public class BlogVisitor {

    @TableId(type = IdType.AUTO)
    private Long visitorId;
    private String fingerprint;
    private String ipAddress;
    private String userAgent;
    private String country;
    private String province;
    private String city;
    private Integer isBlocked;
    private LocalDateTime blockedUntil;
    private LocalDateTime firstVisitTime;
    private LocalDateTime lastVisitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
