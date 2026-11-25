package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本人资料不包含密码哈希和第三方身份标识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户ID
    private Long userId;

    // 用户名
    private String username;

    // 昵称
    private String nickname;

    // 电子邮箱
    private String email;

    // 头像URL
    private String avatar;

    // 个人简介
    private String intro;

    // 状态
    private Integer status;

    // 是否已设置密码
    private boolean hasPassword;

    // 最后登录时间
    private LocalDateTime lastLoginTime;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
