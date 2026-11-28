package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 权限和存储检查通过后的文件流描述，按类型限长（最多60MiB），本地走文件路径、OSS走输入流，避免整包入堆。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContent {

    // 文件名
    private String name;

    // MIME类型
    private String mimeType;

    // 文件字节大小
    private long sizeBytes;

    // 本地文件路径（本地存储时非空）
    private Path localPath;

    // 远端文件输入流（对象存储时非空，由响应写出阶段负责关闭）
    private InputStream stream;
}
