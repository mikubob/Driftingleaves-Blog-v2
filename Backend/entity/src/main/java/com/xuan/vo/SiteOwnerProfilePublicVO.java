package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开主人资料不含用户ID、登录邮箱或管理时间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteOwnerProfilePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
}
