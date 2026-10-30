package com.xuan.vo;



/** 绑定成功的结果只返回 binding。 */
public record OAuthBindResultVO(String operation, OAuthBindingVO binding) { }
