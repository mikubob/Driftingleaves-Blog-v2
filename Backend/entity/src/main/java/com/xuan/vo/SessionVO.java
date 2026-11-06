package com.xuan.vo;

/** 登录成功后返回有效秒数与本人资料。 */
public record SessionVO(int expiresIn, MeVO user) {
}
