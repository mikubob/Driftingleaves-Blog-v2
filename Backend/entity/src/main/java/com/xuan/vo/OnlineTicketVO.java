package com.xuan.vo;

/** 一次性入场票据不是本站登录凭据。 */
public record OnlineTicketVO(String ticket, int expiresIn) {
}
