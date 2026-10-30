package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 一个本站用户和一个规范化邮箱各只能有一条订阅，验证状态不从用户资料推断。 */
@Data
@TableName("blog_rss_subscription")
public class BlogRssSubscription {
    @TableId(type = IdType.AUTO)
    private Long subscriptionId;
    private Long userId;
    private String email;
    private Integer status;
    private LocalDateTime verifiedAt;
    private LocalDateTime subscribeTime;
    private LocalDateTime unsubscribeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
