package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 全站展示资料，启停与账号身份无关，时间由数据库维护。 */
@Data
@TableName("home_social_link")
public class HomeSocialLink {
    @TableId(type = IdType.AUTO)
    private Long socialId;
    private String name;
    private String icon;
    private String url;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
