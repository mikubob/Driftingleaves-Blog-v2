package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限和存储检查通过后读取的文件，按类型限长（最多60MiB），响应前已关闭存储流。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文件名
    private String name;

    // MIME类型
    private String mimeType;

    // 文件字节内容
    private byte[] bytes;
}
