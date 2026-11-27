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
 * 每个用户保留一条订阅，待验证或已订阅记录独占邮箱。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("blog_rss_subscription")
public class BlogRssSubscription implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 订阅ID
    @TableId(type = IdType.AUTO)
    private Long subscriptionId;

    // 用户ID
    private Long userId;

    // 电子邮箱
    private String email;

    // 状态
    private Integer status;

    // 验证时间
    private LocalDateTime verifiedAt;

    // 订阅时间
    private LocalDateTime subscribeTime;

    // 退订时间
    private LocalDateTime unsubscribeTime;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
