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
 * 一个本站用户和一个规范化邮箱各只能有一条订阅，验证状态不从用户资料推断。
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
