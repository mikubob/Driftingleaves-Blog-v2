package com.xuan.vo;

/**
 * 公开主人资料不含用户ID、登录邮箱或管理时间。
 */
public record HomeProfilePublicVO(String nickname, String tagline, String description, String avatar, String website,
                                  String publicEmail, String githubUrl, String location) {

}
