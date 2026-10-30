package com.xuan.vo;

import java.time.LocalDateTime;

/** 本人资料不包含密码哈希和第三方身份标识。 */
public record MeVO(Long userId, String username, String nickname, String email, String avatar, String intro, Integer status, boolean hasPassword, LocalDateTime lastLoginTime, LocalDateTime createTime, LocalDateTime updateTime) { }
