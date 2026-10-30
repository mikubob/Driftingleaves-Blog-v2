package com.xuan.vo;

/** 菜单资料和版本一起传递，版本只放 HTTP 响应头。 */
public record MenuProfileVO(MenuVO menu, String revision) { }
