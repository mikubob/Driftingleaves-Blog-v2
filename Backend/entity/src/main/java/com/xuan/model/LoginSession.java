package com.xuan.model;

import com.xuan.vo.SessionVO;

/**
 * 登录事务的内部结果模型，Token 只交给 HTTP 入口写入 Cookie，不在日志或字符串化时输出。
 */
public record LoginSession(String token, SessionVO response) {

    @Override
    public String toString() {
        return "LoginSession";
    }
}
