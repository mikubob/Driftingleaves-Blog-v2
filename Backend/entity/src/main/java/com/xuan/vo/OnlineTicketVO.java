package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一次性入场票据不是本站登录凭据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineTicketVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 一次性票据
    private String ticket;

    // 过期时间（秒）
    private int expiresIn;
}
