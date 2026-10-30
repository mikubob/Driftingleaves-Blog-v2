package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 不可编辑的管理操作审计；仅由服务端写入固定元数据和脱敏摘要。 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    /** 数据库生成的审计 ID。 */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    /** 操作前从有效会话取得的操作者，物理删除账号时由外键置空。 */
    @TableField("user_id")
    private Long userId;
    /** 服务端声明的操作类型。 */
    @TableField("operation_type")
    private String operationType;
    /** 服务端声明的目标资源。 */
    @TableField("operation_target")
    private String operationTarget;
    /** 显式参数或创建结果中的目标 ID。 */
    @TableField("target_id")
    private Long targetId;
    /** 业务检查的权限快照，不授予权限。 */
    @TableField("permission_code")
    private String permissionCode;
    /** 预留白名单摘要；当前仅记元数据，固定为 null。 */
    @TableField("operation_data")
    private String operationData;
    /** 0 失败、1 成功。 */
    @TableField("result")
    private Integer result;
    /** 固定白名单错误码，不存异常消息。 */
    @TableField("error_message")
    private String errorMessage;
    /** 按受信代理链解析的规范化 IP。 */
    @TableField("ip_address")
    private String ipAddress;
    /** 操作发起时的 UTC 时间。 */
    @TableField("operation_time")
    private LocalDateTime operationTime;
}
