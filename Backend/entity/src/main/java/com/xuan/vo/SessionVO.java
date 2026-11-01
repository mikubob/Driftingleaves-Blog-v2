package com.xuan.vo;

/**
 * 本站 Token 只在登录成功时返回；前端按 expiresIn 保存到期时间，不写 URL 或日志。
 */
public record SessionVO(String tokenType, String accessToken, int expiresIn, MeVO user) {

}
