package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录成功的结果只返回 session。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 第三方授权操作类型
    private String operation;

    // 登录会话信息
    private SessionVO session;
}
