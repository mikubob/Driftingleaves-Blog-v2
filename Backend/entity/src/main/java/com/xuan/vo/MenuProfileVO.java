package com.xuan.vo;

/** 菜单详情及业务版本；版本只通过 HTTP 响应头公开。 */
public record MenuProfileVO(MenuVO menu, String revision) { }
