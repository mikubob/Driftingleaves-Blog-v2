package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDateTime;

/** 单站展示资料固定为1，联系邮箱与登录资料独立。 */
@Data
@TableName("home_profile")
public class HomeProfile {
    @TableId(type = IdType.INPUT)
    private Long profileId;
    private Long userId;
    private String nickname;
    private String tagline;
    private String description;
    private String avatar;
    private String website;
    private String publicEmail;
    private String githubUrl;
    private String location;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
