package com.xuan.vo;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 返回文件资料、公开图片标记和后台预览地址，不返回存储键或 Bucket。
 */
public record FileVO(Long fileId, Long uploaderId, String originalName, String mimeType,
                     BigInteger sizeBytes, String storageType, LocalDateTime createTime, String previewUrl,
                     boolean publicImage) {

}
