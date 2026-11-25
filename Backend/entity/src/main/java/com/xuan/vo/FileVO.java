package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回后台文件资料，存储文件名只取文件名，不返回目录或 Bucket。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文件ID
    private Long fileId;

    // 上传者ID
    private Long uploaderId;

    // 原始文件名
    private String originalName;

    // 存储文件名
    private String storedName;

    // MIME类型
    private String mimeType;

    // 文件大小（字节）
    private BigInteger sizeBytes;

    // 存储类型
    private String storageType;

    // 创建时间
    private LocalDateTime createTime;

    // 预览URL
    private String previewUrl;

    // 是否公开图片
    private boolean publicImage;

    // 使用说明
    private String usage;

    // 引用位置列表
    private List<String> usages;
}
