package com.xuan.mapper;

import com.xuan.entity.SysOperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 这里只写操作日志，不提供日志管理接口。 */
@Mapper
public interface OperationLogMapper {
    /**
     * 日志不提供编辑；成功日志随业务事务写，失败日志等回滚后在新事务写。
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
