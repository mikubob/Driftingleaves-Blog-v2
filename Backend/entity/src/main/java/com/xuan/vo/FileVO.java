package com.xuan.vo;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回后台文件资料，存储文件名只取文件名，不返回目录或 Bucket。
 */
public record FileVO(Long fileId, Long uploaderId, String originalName, String storedName, String mimeType,
                     BigInteger sizeBytes, String storageType, LocalDateTime createTime, String previewUrl,
                     boolean publicImage, String usage, List<String> usages) {

}
