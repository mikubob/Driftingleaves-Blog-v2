package com.xuan.vo;



/** 仅登录成功返回本站 Token，不写 URL 或浏览器存储。 */
public record SessionVO(String tokenType, String accessToken, int expiresIn, MeVO user) { }
