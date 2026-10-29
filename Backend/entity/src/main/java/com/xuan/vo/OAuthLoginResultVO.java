package com.xuan.vo;



/** LOGIN 联合分支仅返回 session。 */
public record OAuthLoginResultVO(String operation, SessionVO session) { }
