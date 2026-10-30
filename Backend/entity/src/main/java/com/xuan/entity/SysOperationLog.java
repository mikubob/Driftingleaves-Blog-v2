package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 操作日志只由服务端写，不提供编辑；只记固定资料和已去掉敏感信息的摘要。 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    /** 数据库生成的审计 ID。 */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    /** 操作前从有效会话里取操作者，账号被物理删除后由外键置空。 */
    @TableField("user_id")
    private Long userId;
    /** 服务端声明的操作类型。 */
    @TableField("operation_type")
    private String operationType;
    /** 服务端声明的目标资源。 */
    @TableField("operation_target")
    private String operationTarget;
    /** 明确参数或创建结果中的目标 ID。 */
    @TableField("target_id")
    private Long targetId;
    /** 这里只记录业务检查过的权限，不会因此给用户授权。 */
    @TableField("permission_code")
    private String permissionCode;
    /** 摘要字段先保留，目前只记操作基本资料，这里固定为 null。 */
    @TableField("operation_data")
    private String operationData;
    /** 0 失败、1 成功。 */
    @TableField("result")
    private Integer result;
    /** 只记名单里的固定错误码，不存异常消息。 */
    @TableField("error_message")
    private String errorMessage;
    /** 按可信代理链取出的 IP，已整理成统一写法。 */
    @TableField("ip_address")
    private String ipAddress;
    /** 操作发起时的 UTC 时间。 */
    @TableField("operation_time")
    private LocalDateTime operationTime;
}
