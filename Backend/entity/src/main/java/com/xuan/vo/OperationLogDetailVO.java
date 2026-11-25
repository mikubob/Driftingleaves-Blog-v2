package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 详情只展示服务端白名单摘要，当前所有写入者都只记基本信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 日志ID
    private Long logId;

    // 用户ID
    private Long userId;

    // 操作类型
    private String operationType;

    // 操作目标
    private String operationTarget;

    // 目标ID
    private Long targetId;

    // 权限编码
    private String permissionCode;

    // 操作结果
    private Integer result;

    // 错误信息
    private String errorMessage;

    // IP地址
    private String ipAddress;

    // 操作时间
    private LocalDateTime operationTime;

    // 操作数据详情
    private Map<String, Object> operationData;
}
