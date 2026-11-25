package com.xuan.model;

import com.xuan.vo.SessionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录事务的内部结果模型，Token 只交给 HTTP 入口写入 Cookie，不在日志或字符串化时输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 登录令牌
    private String token;

    // 会话响应数据
    private SessionVO response;

    @Override
    public String toString() {
        return "LoginSession";
    }
}
