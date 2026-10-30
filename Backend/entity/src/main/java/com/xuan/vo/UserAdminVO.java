package com.xuan.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 后台用户资料包含全部角色，不带密码哈希、平台身份或会话。 */
public record UserAdminVO(Long userId, String username, String nickname, String email, String avatar,
                          String intro, Integer status, boolean hasPassword, LocalDateTime lastLoginTime,
                          LocalDateTime createTime, LocalDateTime updateTime, List<Long> roleIds) { }
