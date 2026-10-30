package com.xuan.mapper;

import com.xuan.entity.SysOperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 审计专用写入边界，不提供日志管理 CRUD。 */
@Mapper
public interface OperationLogMapper {
    /**
     * 写入不可编辑记录；成功记录使用调用方事务，失败记录使用回滚后的独立事务。
     * @param log 服务端元数据和固定脱敏错误，operationData 目前为 null
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO sys_operation_log
                (user_id, operation_type, operation_target, target_id, permission_code,
                 operation_data, result, error_message, ip_address, operation_time)
            VALUES (#{userId}, #{operationType}, #{operationTarget}, #{targetId}, #{permissionCode},
                    #{operationData}, #{result}, #{errorMessage}, #{ipAddress}, #{operationTime})
            """)
    int insertLog(SysOperationLog log);
}
