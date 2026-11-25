package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录成功后返回有效秒数与本人资料。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 过期时间（秒）
    private int expiresIn;

    // 用户信息
    private MeVO user;
}
