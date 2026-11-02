package com.xuan.model;

/**
 * 权限和存储检查通过后读取的文件，按类型限长（最多60MiB），响应前已关闭存储流。
 */
public record FileContent(String name, String mimeType, byte[] bytes) {

}
