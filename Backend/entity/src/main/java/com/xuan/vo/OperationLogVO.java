package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 列表不展开操作摘要，也不返回请求体或内部异常。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogVO implements Serializable {

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

    // 选中条数
    private String selectedCount;
}
