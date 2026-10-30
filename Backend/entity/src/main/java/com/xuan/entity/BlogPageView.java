package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 统计标识不授予身份；IP只存脱敏值，时间由服务端确定。 */
@Data
@TableName("blog_page_view")
public class BlogPageView {
    @TableId(type = IdType.AUTO)
    private Long viewId;
    private Long visitorId;
    private Long userId;
    private Long articleId;
    private String siteCode;
    private String pagePath;
    private String pageTitle;
    private String referer;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime viewTime;
}
