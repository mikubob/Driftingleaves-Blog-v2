package com.xuan.model;


/** 资料和版本一起读，Controller 只把版本放响应头。 */
public record ResourceVersion<T>(T value, String revision) { }
