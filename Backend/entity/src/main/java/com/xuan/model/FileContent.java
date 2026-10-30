package com.xuan.model;

/** 权限和实际存储检查通过后读取的图片，最多 10MiB，响应前已关闭存储流。 */
public record FileContent(String name, String mimeType, byte[] bytes) { }
