package com.xuan.vo;



/** 登录成功的结果只返回 session。 */
public record OAuthLoginResultVO(String operation, SessionVO session) { }
