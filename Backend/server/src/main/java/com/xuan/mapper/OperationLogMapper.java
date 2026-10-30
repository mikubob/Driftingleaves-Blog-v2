package com.xuan.mapper;

import com.xuan.entity.SysOperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.vo.OperationLogVO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 只允许服务端写日志，后台提供独立查询与清理，不开放编辑。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<SysOperationLog> {
    /** 列表明确排除摘要列，筛选与总数一致。 */
    IPage<OperationLogVO> logPage(IPage<OperationLogVO> page, @Param("query") OperationLogQueryDTO query,
            @Param("user") Long user, @Param("target") Long target,
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
    /** 清理事务先锁操作者，再锁日志并重新检查本次ALL权限。 */
    SysOperationLog lockById(@Param("id") long id);
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
