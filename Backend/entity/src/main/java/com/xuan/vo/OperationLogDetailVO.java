package com.xuan.vo;

import java.util.Map;

import java.time.LocalDateTime;

/** 详情只展示服务端白名单摘要，当前所有写入者都只记基本信息。 */
public record OperationLogDetailVO(Long logId, Long userId, String operationType, String operationTarget, Long targetId, String permissionCode, Integer result, String errorMessage, String ipAddress, LocalDateTime operationTime, Map<String, Object> operationData) { }
