package com.xuan.vo;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 只给文件展示资料和需 Bearer 的预览地址，不能返回存储键或 Bucket。
 */
public record FileVO(Long fileId, Long uploaderId, String originalName, String mimeType,
                     BigInteger sizeBytes, String storageType, LocalDateTime createTime, String previewUrl) {

}
