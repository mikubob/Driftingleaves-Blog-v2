package com.xuan.model;

import com.xuan.vo.SessionVO;

/** 登录事务的内部结果，Token 只交给 HTTP 入口写入 Cookie。 */
public record LoginSession(String token, SessionVO response) {

    @Override
    public String toString() {
        return "LoginSession";
    }
}
