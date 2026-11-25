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
@TableName("blog_page_view")
public class BlogPageView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 浏览记录ID
    @TableId(type = IdType.AUTO)
    private Long viewId;

    // 访客ID
    private Long visitorId;

    // 用户ID
    private Long userId;

    // 文章ID
    private Long articleId;

    // 站点编码
    private String siteCode;

    // 页面路径
    private String pagePath;

    // 页面标题
    private String pageTitle;

    // 来源页面
    private String referer;

    // IP地址
    private String ipAddress;

    // User-Agent
    private String userAgent;

    // 浏览时间
    private LocalDateTime viewTime;
}
