package com.xuan.vo;



/** 本站 Token 只在登录成功时返回，不写 URL 或浏览器存储。 */
public record SessionVO(String tokenType, String accessToken, int expiresIn, MeVO user) { }
