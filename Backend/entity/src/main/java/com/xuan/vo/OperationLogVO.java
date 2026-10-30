package com.xuan.vo;

import java.time.LocalDateTime;

/** 列表不展开操作摘要，也不返回请求体或内部异常。 */
public record OperationLogVO(Long logId, Long userId, String operationType, String operationTarget, Long targetId, String permissionCode, Integer result, String errorMessage, String ipAddress, LocalDateTime operationTime) { }
