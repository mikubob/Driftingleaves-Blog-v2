package com.xuan.vo;



/** 供 Controller 设置版本响应头的内部查询结果。 */
public record UserProfileVO(MeVO user, String revision) { }
