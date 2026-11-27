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
 * 保存全站共用的站长公开资料，固定记录ID为1。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("site_owner_profile")
public class SiteOwnerProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 站长资料ID
    @TableId(type = IdType.INPUT)
    private Long profileId;

    // 昵称
    private String nickname;

    // 个人标语
    private String tagline;

    // 描述
    private String description;

    // 头像URL
    private String avatar;

    // 个人网站地址
    private String website;

    // 公开联系邮箱
    private String publicEmail;

    // GitHub地址
    private String githubUrl;

    // 属地信息
    private String location;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
